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
QUERIES = {
    'tables': """SELECT JSON_ARRAY(table_name, engine, table_collation)
        FROM information_schema.tables WHERE table_schema=DATABASE()
        AND table_type='BASE TABLE' ORDER BY table_name""",
    'columns': """SELECT JSON_ARRAY(table_name, column_name, column_type,
        is_nullable, column_default, extra, generation_expression, character_set_name, collation_name)
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
    return [SCHEMA, menus / '2_menu.sql', *sorted((ROOT / 'sql/2_ini_data').glob('*.sql')),
            *sorted(menus.glob('202*.sql'))]


class Mysql:
    def __init__(self, args):
        self.command = [args.mysql, '--no-defaults', '--protocol=TCP', f'--host={args.host}', f'--port={args.port}',
                        f'--user={args.user}', '--connect-timeout=5', '--default-character-set=utf8mb4',
                        '--batch', '--raw', '--skip-column-names']

    def run(self, sql, database=None):
        command = self.command + ([f'--database={identifier(database)}'] if database else [])
        result = subprocess.run(command, input=sql, text=True, capture_output=True, timeout=120)
        if result.returncode:
            raise RuntimeError(result.stderr.strip())
        return result.stdout.strip()

    def schema(self, database):
        return {name: [json.loads(line) for line in self.run(query, database).splitlines()]
                for name, query in QUERIES.items()}


def differences(expected, actual):
    issues = []
    for section in QUERIES:
        left = {json.dumps(row, ensure_ascii=False) for row in expected[section]}
        right = {json.dumps(row, ensure_ascii=False) for row in actual[section]}
        issues.extend(f'{section}: missing/different {row}' for row in sorted(left - right))
        issues.extend(f'{section}: unexpected/different {row}' for row in sorted(right - left))
    return issues


def assert_true(condition, message):
    if not condition:
        raise RuntimeError(message)


def verify(args, mysql):
    database = 'aio_fresh_' + uuid.uuid4().hex[:12]
    mysql.run(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
    try:
        files = initialization_files()
        for path in files:
            mysql.run(scoped_sql(path.read_text()), database)
            print(f'Initialized {path.relative_to(ROOT)}')
        expected = mysql.schema(database)
        assert_true(mysql.run("SELECT COUNT(*) FROM sys_menu WHERE name='Analytics' AND path='/'", database) == '1',
                    'Home seed must use the current root route')
        for name in ('StorageAdmin', 'BankCardCoverAdmin', 'AccessLog', 'OperationLog'):
            assert_true(mysql.run(f"SELECT COUNT(*) FROM sys_menu WHERE name='{name}' AND roles='admin'", database) == '1',
                        f'Missing administrator menu: {name}')
        assert_true(mysql.run("SELECT COUNT(*) FROM sys_menu WHERE name='OperationLog' AND mobile_status=0", database) == '1',
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
        contract = {'version': 1, 'schema_sha256': hashlib.sha256(scoped_sql(SCHEMA.read_text()).encode()).hexdigest(), 'schema': expected}
        (args.report_dir / 'schema-contract.json').write_text(json.dumps(contract, ensure_ascii=False, indent=2) + '\n')
        report = {'files': [str(path.relative_to(ROOT)) for path in files],
                  'mysql_version': mysql.run('SELECT VERSION()'), 'result': 'passed',
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
    check = commands.add_parser('verify', help='Create/drop only randomly named disposable databases; run on an isolated test MySQL')
    check.add_argument('--report-dir', type=Path, default=ROOT / 'artifacts/database-schema')
    read = commands.add_parser('preflight', help='SELECT only: compare schema with a release artifact')
    read.add_argument('--database', required=True, type=identifier)
    read.add_argument('--expected', required=True, type=Path)
    args = parser.parse_args()
    mysql = Mysql(args)
    if args.command == 'verify':
        verify(args, mysql)
    else:
        contract = json.loads(args.expected.read_text())
        assert_true(contract['version'] == 1, 'Unsupported schema contract version')
        issues = differences(contract['schema'], mysql.schema(args.database))
        assert_true(not issues, 'Database preflight failed:\n' + '\n'.join(issues))
        print('Database preflight passed (schema only, SELECT only).')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, RuntimeError, subprocess.SubprocessError) as error:
        print(f'ERROR: {error}', file=sys.stderr)
        sys.exit(1)
