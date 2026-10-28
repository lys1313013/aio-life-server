#!/usr/bin/env python3
"""Verify the current schema and seeds in a disposable database, or inspect a database read-only.

Credentials are read by the mysql client from MYSQL_PWD;
they are never passed on the command line or written to reports.
"""
import argparse
import hashlib
import json
import re
import subprocess
import sys
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCHEMA = ROOT / 'sql/1_init_table/2026-08-18_init_all_tables.sql'
MIGRATIONS = [ROOT / 'sql/migrations' / name for name in (
    '2026-10-28_01_asset_purchase_source.sql',
    '2026-10-28_02_unify_common_fields.sql',
    '2026-10-28_03_schema_alignment.sql',
    '2026-10-28_04_schema_comments.sql',
)]
CONTRACT_VERSION = 2
QUERIES = {
    'tables': """SELECT JSON_ARRAY(table_name, engine, table_collation, table_comment)
        FROM information_schema.tables WHERE table_schema=DATABASE()
        AND table_type='BASE TABLE' ORDER BY table_name""",
    'columns': """SELECT JSON_ARRAY(table_name, column_name, column_type,
        is_nullable, column_default, extra, generation_expression, character_set_name, collation_name, column_comment)
        FROM information_schema.columns WHERE table_schema=DATABASE()
        ORDER BY table_name,column_name""",
    'indexes': """SELECT JSON_ARRAY(table_name,index_name,non_unique,seq_in_index,
        column_name,sub_part,index_type,collation,is_visible,expression)
        FROM information_schema.statistics WHERE table_schema=DATABASE()
        ORDER BY table_name,index_name,seq_in_index""",
    'checks': """SELECT JSON_ARRAY(t.table_name,t.constraint_name,c.check_clause,t.enforced)
        FROM information_schema.table_constraints t
        JOIN information_schema.check_constraints c
          ON t.constraint_schema=c.constraint_schema AND t.constraint_name=c.constraint_name
        WHERE t.constraint_schema=DATABASE() AND t.constraint_type='CHECK'
        ORDER BY t.table_name,t.constraint_name""",
    'foreign_keys': """SELECT JSON_ARRAY(k.table_name,k.constraint_name,k.column_name,
        k.ordinal_position,k.referenced_table_name,k.referenced_column_name,r.update_rule,r.delete_rule)
        FROM information_schema.key_column_usage k
        JOIN information_schema.referential_constraints r
          ON k.constraint_schema=r.constraint_schema AND k.constraint_name=r.constraint_name
        WHERE k.constraint_schema=DATABASE() AND k.referenced_table_name IS NOT NULL
        ORDER BY k.table_name,k.constraint_name,k.ordinal_position""",
}
ROW_LENGTHS = {'tables': 4, 'columns': 10, 'indexes': 10, 'checks': 4, 'foreign_keys': 8}


def mysql_version(value):
    match = re.match(r'^(\d+)\.(\d+)\.(\d+)', value)
    if not match or 'mariadb' in value.lower():
        raise ValueError(f'Unsupported MySQL version: {value}')
    version = tuple(int(part) for part in match.groups())
    if version < (5, 7, 8):
        raise ValueError(f'Metadata inspection requires MySQL 5.7.8 or newer: {value}')
    return version


def queries_for_version(version):
    queries = QUERIES.copy()
    if version < (8, 0, 13):
        # 5.7 has neither functional indexes nor index visibility metadata.
        visibility = 'is_visible' if version >= (8, 0, 0) else "'YES'"
        queries['indexes'] = queries['indexes'].replace('is_visible,expression', f'{visibility},NULL')
    if version < (8, 0, 16):
        # CHECK syntax was ignored before 8.0.16. Do not query nonexistent metadata
        # or synthesize constraints that the server cannot enforce.
        queries['checks'] = 'SELECT JSON_ARRAY(NULL,NULL,NULL,NULL) FROM DUAL WHERE 1=0'
    return queries


def identifier(value):
    if not re.fullmatch(r'[A-Za-z0-9_]+', value):
        raise ValueError('Database name must contain only letters, digits and underscores')
    return value


def scoped_sql(source):
    """Discard only the bootstrap database directives; mysql selects our scratch DB."""
    source = re.sub(r'(?im)^\s*create\s+database\s+if\s+not\s+exists\s+`?aio_life`?\s*;\s*$', '', source)
    source = re.sub(r'(?im)^\s*use\s+`?aio_life`?\s*;\s*$', '', source)
    executable = re.sub(r'--[^\n]*|/\*.*?\*/', '', source, flags=re.S)
    if re.search(r'\b(?:use\s+|(?:create|drop|alter)\s+database\s+|`?aio_life`?\s*\.)', executable, re.I):
        raise ValueError('SQL contains a database directive outside the disposable database')
    return source


def initialization_files():
    """Use the same deterministic order as the documented fresh-database entry."""
    menus = ROOT / 'sql/menu/2_ini_data'
    return [SCHEMA, *MIGRATIONS, menus / '2_menu.sql', *sorted((ROOT / 'sql/2_ini_data').glob('*.sql')),
            *sorted(menus.glob('202*.sql'))]


def initialization_manifest(paths):
    return [{'path': path.relative_to(ROOT).as_posix(), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
            for path in paths]


def manifest_digest(manifest):
    return hashlib.sha256(json.dumps(manifest, ensure_ascii=False, separators=(',', ':')).encode()).hexdigest()


def validate_contract(contract):
    assert_true(contract.get('version') == CONTRACT_VERSION,
                'Schema contract version 2 is required; regenerate it with the current verify command. '
                'Older contracts lack COMMENT metadata and the complete initialization source hashes.')
    sources = initialization_manifest(initialization_files())
    assert_true(contract.get('initialization_sources') == sources
                and contract.get('initialization_sha256') == manifest_digest(sources),
                'Schema contract sources differ from the current initialization files; regenerate with verify.')
    expected = contract.get('schema', {})
    assert_true(set(expected) == set(QUERIES), 'Schema contract metadata sections are incomplete.')
    for section, size in ROW_LENGTHS.items():
        assert_true(isinstance(expected[section], list)
                    and all(isinstance(row, list) and len(row) == size for row in expected[section]),
                    f'Schema contract {section} rows are incomplete.')


class Mysql:
    def __init__(self, args):
        self.command = [args.mysql, '--no-defaults', '--protocol=TCP', f'--host={args.host}', f'--port={args.port}',
                        f'--user={args.user}', '--connect-timeout=5', '--default-character-set=utf8mb4',
                        '--batch', '--raw', '--skip-column-names']
        self._version_string = None

    def run(self, sql, database=None):
        command = self.command + ([f'--database={identifier(database)}'] if database else [])
        result = subprocess.run(command, input=sql, text=True, capture_output=True, timeout=120)
        if result.returncode:
            raise RuntimeError(result.stderr.strip())
        return result.stdout.strip()

    def version_string(self):
        if self._version_string is None:
            self._version_string = self.run('SELECT VERSION()')
        return self._version_string

    def version(self):
        return mysql_version(self.version_string())

    def schema(self, database):
        return {name: [json.loads(line) for line in self.run(query, database).splitlines()]
                for name, query in queries_for_version(self.version()).items()}


def normalize_expression(value):
    """Normalize formatting outside literals, retaining operator grouping and string case."""
    tokens = re.findall(r"'(?:''|\\.|[^'])*'|\"(?:\"\"|\\.|[^\"])*\"|`(?:``|[^`])*`|"
                        r'[\w$]+|<=>|<=|>=|<>|!=|<<|>>|&&|\|\||[^\s]', value)
    tokens = [token if token[0] in "'\"`" else token.lower() for token in tokens]
    while tokens and tokens[0] == '(' and tokens[-1] == ')':
        depth = 0
        for position, token in enumerate(tokens):
            depth += (token == '(') - (token == ')')
            if depth == 0:
                break
        if position != len(tokens) - 1:
            break
        tokens = tokens[1:-1]
    return ' '.join(tokens)


def normalize_row(section, row):
    row = list(row)
    if section == 'columns':
        # Integer display width does not change the stored range. ZEROFILL width
        # affects output formatting, so keep it significant.
        if 'zerofill' not in row[2].lower():
            row[2] = re.sub(r'^(tinyint|smallint|mediumint|int|bigint)\(\d+\)', r'\1', row[2].lower())
        if re.match(r'^(timestamp|datetime)\b', row[2]) and isinstance(row[4], str):
            row[4] = re.sub(r'^current_timestamp(?:\(\))?$', 'CURRENT_TIMESTAMP', row[4], flags=re.I)
        row[5] = re.sub(r'\s+', ' ', row[5].lower().replace('default_generated', '')
                        .replace('current_timestamp()', 'current_timestamp')).strip()
        row[6] = normalize_expression(row[6])
    elif section == 'foreign_keys':
        # InnoDB checks both actions immediately; neither is deferrable.
        row[6:] = ['RESTRICT' if value == 'NO ACTION' else value for value in row[6:]]
    elif section == 'indexes' and row[9] is not None:
        row[9] = normalize_expression(row[9])
    elif section == 'checks':
        row[2] = normalize_expression(row[2])
    return row


def differences(expected, actual, actual_version=None):
    issues = []
    for section in QUERIES:
        if section == 'checks' and actual_version is not None and actual_version < (8, 0, 16) and expected[section]:
            issues.append(f'platform: MySQL {".".join(map(str, actual_version))} cannot enforce '
                          f'{len(expected[section])} expected CHECK constraints; requires MySQL 8.0.16 or newer.')
            continue
        left = {json.dumps(normalize_row(section, row), ensure_ascii=False) for row in expected[section]}
        right = {json.dumps(normalize_row(section, row), ensure_ascii=False) for row in actual[section]}
        issues.extend(f'{section}: missing/different {row}' for row in sorted(left - right))
        issues.extend(f'{section}: unexpected/different {row}' for row in sorted(right - left))
    return issues


def assert_true(condition, message):
    if not condition:
        raise RuntimeError(message)


def verify(args, mysql):
    assert_true(mysql.version() >= (8, 0, 16),
                'Fresh-database verification requires MySQL 8.0.16 or newer, including enforced CHECK constraints; '
                'use preflight for read-only MySQL 5.7 inspection.')
    files = initialization_files()
    sources = initialization_manifest(files)
    database = 'aio_fresh_' + uuid.uuid4().hex[:12]
    mysql.run(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
    try:
        for path in files:
            mysql.run(scoped_sql(path.read_text()), database)
            print(f'Initialized {path.relative_to(ROOT)}')
        expected = mysql.schema(database)
        assert_true(mysql.run("SELECT COUNT(*) FROM sys_menu WHERE name='Analytics' AND path='/'", database) == '1',
                    'Home seed must use the current root route')
        for name in ('StorageAdmin', 'BankCardCoverAdmin', 'AccessLog', 'OperationLog'):
            assert_true(mysql.run(f"SELECT COUNT(*) FROM sys_menu WHERE name='{name}' AND roles='admin'", database) == '1',
                        f'Missing administrator menu: {name}')
        assert_true(mysql.run("SELECT COUNT(*) FROM sys_menu WHERE name='OperationLog' AND mobile_is_enabled=0", database) == '1',
                    'Operation log must remain Web-only')
        assert_true(not any(row[0] in ('llm_key', 'conversation', 'chat_message') for row in expected['tables']),
                    'Removed LLM tables must not be initialized')
        # Exercise current constraints, without maintaining a historical fixture schema.
        mysql.run("INSERT INTO user(id,username,nickname,phone_country_code,phone,wechat_openid) "
                  "VALUES(-900002,'schema_wechat','fixture','86','13800138000','fixture_openid')", database)
        for label, statement, code in [
            ('phone uniqueness', "INSERT INTO user(id,username,nickname,phone_country_code,phone) VALUES(-900003,'duplicate','fixture','86','13800138000')", '1062'),
            ('openid uniqueness', "INSERT INTO user(id,username,nickname,wechat_openid) VALUES(-900003,'duplicate','fixture','fixture_openid')", '1062'),
            ('phone pair', "INSERT INTO user(id,username,nickname,phone) VALUES(-900003,'invalid','fixture','13800138001')", '3819'),
        ]:
            try:
                mysql.run(statement, database)
            except RuntimeError as error:
                assert_true(f'ERROR {code} ' in str(error), f'Unexpected failure for {label}: {error}')
            else:
                raise RuntimeError(f'Constraint did not enforce {label}')
        mysql.run("UPDATE user SET is_deleted=1 WHERE id=-900002;"
                  "INSERT INTO user(id,username,nickname,phone_country_code,phone,wechat_openid) "
                  "VALUES(-900004,'reused','fixture','86','13800138000','fixture_openid');"
                  "INSERT INTO bank_card(id,user_id,custom_bank_name,card_type,create_user,update_user) "
                  "VALUES(-900002,-900004,'fixture bank','debit',-900004,-900004)", database)
        file_id = 'abcdef0123456789abcdef0123456789'
        mysql.run(f"INSERT INTO file(id,file_name,biz_type,is_public,create_user,create_time,update_time) VALUES('{file_id}','fixture.png','avatar',1,-900004,NOW(),NOW());"
                  f"UPDATE user SET avatar_file_id='{file_id}' WHERE id=-900004", database)
        try:
            mysql.run(f"DELETE FROM file WHERE id='{file_id}'", database)
        except RuntimeError as error:
            assert_true('ERROR 1451 ' in str(error), f'Unexpected avatar reference error: {error}')
        else:
            raise RuntimeError('Referenced avatar file was deleted')
        args.report_dir.mkdir(parents=True, exist_ok=True)
        assert_true(initialization_manifest(files) == sources, 'Initialization files changed during verification; rerun verify.')
        contract = {'version': CONTRACT_VERSION, 'mysql_version': mysql.version_string(),
                    'initialization_sources': sources, 'initialization_sha256': manifest_digest(sources),
                    'schema': expected}
        (args.report_dir / 'schema-contract.json').write_text(json.dumps(contract, ensure_ascii=False, indent=2) + '\n')
        report = {'files': [str(path.relative_to(ROOT)) for path in files],
                  'mysql_version': mysql.version_string(), 'result': 'passed',
                  'checks': ['current schema and seeds', 'current home route', 'admin menus',
                             'phone/openid uniqueness', 'phone pair constraint', 'soft-delete reuse',
                             'optional card number', 'avatar file reference']}
        (args.report_dir / 'schema-report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
        print(f'Schema passed: {len(expected["tables"])} tables; reports: {args.report_dir}')
    finally:
        mysql.run(f'DROP DATABASE `{database}`')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mysql', default='mysql')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=3306)
    parser.add_argument('--user', default='root')
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('files', help='List the ordered initialization SQL paths; no database connection')
    check = commands.add_parser('verify', help='Create/drop only randomly named disposable databases; run on an isolated test MySQL')
    check.add_argument('--report-dir', type=Path, default=ROOT / 'artifacts/database-schema')
    read = commands.add_parser('preflight', help='SELECT only: compare schema with a release artifact')
    read.add_argument('--database', required=True, type=identifier)
    read.add_argument('--expected', required=True, type=Path)
    args = parser.parse_args()
    if args.command == 'files':
        for path in initialization_files():
            assert_true(path.is_file(), f'Missing initialization SQL: {path.relative_to(ROOT)}')
            print(path.relative_to(ROOT).as_posix())
        return
    mysql = Mysql(args)
    if args.command == 'verify':
        verify(args, mysql)
    else:
        contract = json.loads(args.expected.read_text())
        validate_contract(contract)
        issues = differences(contract['schema'], mysql.schema(args.database), mysql.version())
        assert_true(not issues, 'Database preflight failed:\n' + '\n'.join(issues))
        print('Database preflight passed (schema only, SELECT only).')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, RuntimeError, subprocess.SubprocessError) as error:
        print(f'ERROR: {error}', file=sys.stderr)
        sys.exit(1)
