#!/usr/bin/env python3
"""Verify a pinned SQL upgrade in disposable databases, or inspect a database read-only.

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
PLAN = ROOT / 'sql/upgrade-plan.json'
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
    """Discard only the known historical database directives; mysql selects our scratch DB."""
    source = re.sub(r'(?im)^\s*create\s+database\s+if\s+not\s+exists\s+`?aio_life`?\s*;\s*$', '', source)
    source = re.sub(r'(?im)^\s*use\s+`?aio_life`?\s*;\s*$', '', source)
    executable = re.sub(r'--[^\n]*|/\*.*?\*/', '', source, flags=re.S)
    if re.search(r'\b(?:use\s+|(?:create|drop|alter)\s+database\s+|`?aio_life`?\s*\.)', executable, re.I):
        raise ValueError('SQL contains a database directive outside the disposable database')
    return source


def checked_file(entry):
    path = (ROOT / entry['path']).resolve()
    if not path.is_relative_to(ROOT / 'sql'):
        raise ValueError('Migration path must be inside sql/')
    source = path.read_bytes()
    if hashlib.sha256(source).hexdigest() != entry['sha256']:
        raise ValueError(f'Migration checksum differs: {entry["path"]}')
    return source.decode()


def load_plan():
    plan = json.loads(PLAN.read_text())
    base = plan['baseline']
    if plan['version'] != 1 or not re.fullmatch(r'[a-f0-9]{40}', base['revision']):
        raise ValueError('Upgrade baseline must be an immutable 40-character revision')
    source = subprocess.run(['git', 'show', f'{base["revision"]}:{base["schema"]}'],
                            cwd=ROOT, check=True, capture_output=True).stdout
    if hashlib.sha256(source).hexdigest() != base['sha256']:
        raise ValueError('Pinned baseline schema checksum differs')
    paths = [entry['path'] for entry in plan['migrations']]
    if len(paths) != len(set(paths)):
        raise ValueError('Duplicate migration in upgrade plan')
    migrations = [(entry['path'], scoped_sql(checked_file(entry))) for entry in plan['migrations']]
    return plan, scoped_sql(source.decode()), migrations


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
    plan, baseline, migrations = load_plan()
    target = scoped_sql((ROOT / plan['target_schema']).read_text())
    suffix = uuid.uuid4().hex[:12]
    old, fresh = f'aio_upgrade_{suffix}', f'aio_fresh_{suffix}'
    created = []
    try:
        for database in (old, fresh):
            mysql.run(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
            created.append(database)
        mysql.run(baseline, old)
        mysql.run((ROOT / 'sql/upgrade-tests/baseline-data.sql').read_text(), old)
        before = mysql.run("SELECT id,username,password,nickname FROM user WHERE id=-900001;"
                           "SELECT id,card_no_ciphertext,HEX(card_no_fingerprint),card_no_last4 FROM bank_card WHERE id=-900001", old)
        for path, source in migrations:
            mysql.run(source, old)
            print(f'Applied {path}')
        after = mysql.run("SELECT id,username,password,nickname FROM user WHERE id=-900001;"
                          "SELECT id,card_no_ciphertext,HEX(card_no_fingerprint),card_no_last4 FROM bank_card WHERE id=-900001", old)
        assert_true(before == after, 'Upgrade changed existing account or card data')
        assert_true(mysql.run('SELECT COUNT(*) FROM user WHERE id=-900001 AND phone IS NULL AND wechat_openid IS NULL', old) == '1',
                    'Upgrade unexpectedly bound an existing account')
        assert_true(mysql.run("SELECT COUNT(*) FROM sys_menu WHERE name IN ('StorageAdmin','BankCardCoverAdmin') AND roles='admin'", old) == '2',
                    'New administrator menu migrations are incomplete')
        mysql.run(target, fresh)
        expected, actual = mysql.schema(fresh), mysql.schema(old)
        issues = differences(expected, actual)
        assert_true(not issues, 'Upgrade schema differs from fresh schema:\n' + '\n'.join(issues))
        # Exercise the actual migrated constraints, not just the presence of index names.
        mysql.run("INSERT INTO user(id,username,nickname,phone_country_code,phone,wechat_openid) "
                  "VALUES(-900002,'upgrade_wechat','fixture','86','13800138000','fixture_openid')", old)
        for label, statement in [
            ('phone uniqueness', "INSERT INTO user(id,username,nickname,phone_country_code,phone) VALUES(-900003,'duplicate','fixture','86','13800138000')"),
            ('openid uniqueness', "INSERT INTO user(id,username,nickname,wechat_openid) VALUES(-900003,'duplicate','fixture','fixture_openid')"),
            ('phone pair check', "INSERT INTO user(id,username,nickname,phone) VALUES(-900003,'invalid','fixture','13800138001')"),
        ]:
            try:
                mysql.run(statement, old)
            except RuntimeError as error:
                # Do not count connection/syntax errors as evidence of enforced constraints.
                code = '1062' if 'uniqueness' in label else '3819'
                assert_true(f'ERROR {code} ' in str(error), f'Unexpected failure for {label}: {error}')
            else:
                raise RuntimeError(f'Migrated constraint did not enforce {label}')
        mysql.run("UPDATE user SET is_deleted=1 WHERE id=-900002;"
                  "INSERT INTO user(id,username,nickname,phone_country_code,phone,wechat_openid) "
                  "VALUES(-900004,'reused','fixture','86','13800138000','fixture_openid');"
                  "INSERT INTO bank_card(id,user_id,custom_bank_name,card_type,create_user,update_user) "
                  "VALUES(-900002,-900001,'fixture bank','debit',-900001,-900001)", old)
        args.report_dir.mkdir(parents=True, exist_ok=True)
        contract = {'version': 1, 'schema_sha256': hashlib.sha256(target.encode()).hexdigest(), 'schema': expected}
        (args.report_dir / 'schema-contract.json').write_text(json.dumps(contract, ensure_ascii=False, indent=2) + '\n')
        report = {'baseline': plan['baseline'], 'migrations': plan['migrations'],
                  'mysql_version': mysql.run('SELECT VERSION()'), 'result': 'passed',
                  'checks': ['full schema parity', 'existing data retained', 'admin menus',
                             'phone/openid uniqueness', 'phone pair constraint', 'soft-delete reuse', 'optional card number']}
        (args.report_dir / 'upgrade-report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
        print(f'Upgrade passed: {len(expected["tables"])} tables; reports: {args.report_dir}')
    finally:
        for database in reversed(created):
            mysql.run(f'DROP DATABASE `{database}`')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mysql', default='mysql')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=3306)
    parser.add_argument('--user', default='root')
    commands = parser.add_subparsers(dest='command', required=True)
    check = commands.add_parser('verify', help='Create/drop only randomly named disposable databases; run on an isolated test MySQL')
    check.add_argument('--report-dir', type=Path, default=ROOT / 'artifacts/database-upgrade')
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
