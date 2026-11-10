#!/usr/bin/env python3
"""一次性离线迁移工具；业务运行时仅支持 PostgreSQL。

依赖：pip install 'psycopg[binary]' pymysql
snapshot 只读 MySQL，创建私有一致性快照；restore 只接受与种子清单完全一致的 PostgreSQL，
在一个事务中导入、归档原始全部数据并验证逐表内容摘要。连接信息仅从环境变量读取。
"""
import argparse
import base64
import datetime as dt
import decimal
import gzip
import hashlib
import json
import os
from pathlib import Path

import psycopg
from psycopg import sql
from psycopg.types.json import Jsonb, set_json_loads

ROOT = Path(__file__).resolve().parents[1]
SEED_MANIFEST = ROOT / 'sql/postgresql-seed-manifest.json'
# 运行日志默认不迁移，完整只读快照仍可保留作离线备份；Flyway 账本必须保留。
EXCLUDED_LOG_TABLES = frozenset({'api_key_log', 'login_log', 'mail_log', 'sys_activity_log',
                                 'sys_scheduled_job_log', 'data_export_audit_event'})


def encode(value):
    if isinstance(value, bytes):
        return {'$bytes': base64.b64encode(value).decode()}
    if isinstance(value, decimal.Decimal):
        return {'$decimal': str(value)}
    if isinstance(value, dt.datetime):
        return {'$datetime': value.isoformat()}
    if isinstance(value, dt.date):
        return {'$date': value.isoformat()}
    if isinstance(value, dt.timedelta):
        return {'$timedelta': str(value.total_seconds())}
    raise TypeError(type(value).__name__)


def decode(value):
    if '$bytes' in value:
        return base64.b64decode(value['$bytes'])
    if '$decimal' in value:
        return decimal.Decimal(value['$decimal'])
    if '$datetime' in value:
        return dt.datetime.fromisoformat(value['$datetime'])
    if '$date' in value:
        return dt.date.fromisoformat(value['$date'])
    if '$timedelta' in value:
        return dt.timedelta(seconds=float(value['$timedelta']))
    return value


def dumps(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'), default=encode)


def canonical(value):
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float, decimal.Decimal)):
        return {'$number': str(decimal.Decimal(str(value)).normalize())}
    if isinstance(value, list):
        return [canonical(v) for v in value]
    if isinstance(value, dict):
        return {k: canonical(v) for k, v in value.items()}
    return value


def hash_rows(rows):
    digest = hashlib.sha256()
    count = 0
    for row in rows:
        digest.update(dumps(canonical(row)).encode() + b'\n')
        count += 1
    return {'count': count, 'sha256': digest.hexdigest()}


def columns(connection, table):
    return connection.execute("""
        SELECT column_name, data_type, udt_name, is_generated FROM information_schema.columns
        WHERE table_schema='public' AND table_name=%s ORDER BY ordinal_position
        """, (table,)).fetchall()


def tables(connection):
    return [r[0] for r in connection.execute("""
        SELECT tablename FROM pg_tables WHERE schemaname='public'
        AND tablename<>'flyway_schema_history' ORDER BY tablename
        """)]


def pg_rows(connection, table, names):
    # 按主键排序；无主键表直接拒绝，避免依赖不稳定物理顺序。
    primary = connection.execute("""
        SELECT a.attname FROM pg_index i JOIN pg_class t ON t.oid=i.indrelid
        JOIN pg_namespace n ON n.oid=t.relnamespace
        CROSS JOIN LATERAL unnest(i.indkey) WITH ORDINALITY k(attnum,position)
        JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=k.attnum
        WHERE n.nspname='public' AND t.relname=%s AND i.indisprimary ORDER BY k.position
        """, (table,)).fetchall()
    if not primary:
        raise ValueError('Missing primary key: ' + table)
    query = sql.SQL('SELECT {} FROM public.{} ORDER BY {}').format(
        sql.SQL(',').join(map(sql.Identifier, names)), sql.Identifier(table),
        sql.SQL(',').join(sql.Identifier(r[0]) for r in primary))
    with connection.cursor(name='verify_' + table) as cursor:
        cursor.itersize = 5000
        cursor.execute(query)
        yield from (list(row) for row in cursor)


def target_manifest(connection):
    return {table: hash_rows(pg_rows(connection, table, [c[0] for c in columns(connection, table) if c[3]=='NEVER']))
            for table in tables(connection)}


def snapshot(output):
    import pymysql  # 仅源库快照使用，不是应用运行依赖。
    output = output.expanduser().resolve()
    if output.exists():
        raise ValueError('Snapshot directory already exists')
    output.mkdir(mode=0o700, parents=True)
    source = pymysql.connect(host=os.environ['SOURCE_MYSQL_HOST'],
            port=int(os.environ.get('SOURCE_MYSQL_PORT', '3306')),
            user=os.environ['SOURCE_MYSQL_USER'], password=os.environ['SOURCE_MYSQL_PASSWORD'],
            database=os.environ['SOURCE_MYSQL_DATABASE'], charset='utf8mb4', autocommit=False)
    manifest = {'format': 1, 'createdAt': dt.datetime.now(dt.timezone.utc).isoformat(), 'tables': {}}
    try:
        with source.cursor() as cursor:
            cursor.execute('SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ')
            cursor.execute('SET TRANSACTION READ ONLY')
            cursor.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
            cursor.execute("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name")
            names = [r[0] for r in cursor.fetchall()]
        for table in names:
            if not table.replace('_', '').isalnum():
                raise ValueError('Invalid source identifier')
            with source.cursor(pymysql.cursors.DictCursor) as cursor:
                cursor.execute('SELECT * FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=%s ORDER BY ordinal_position', (table,))
                metadata = cursor.fetchall()
            fields = [c['COLUMN_NAME'] for c in metadata]
            path = output / (table + '.jsonl.gz')
            fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
            digest = hashlib.sha256()
            count = 0
            primary = [c['COLUMN_NAME'] for c in metadata if c['COLUMN_KEY']=='PRI']
            order = ' ORDER BY ' + ','.join('`'+n+'`' for n in primary) if primary else ''
            with os.fdopen(fd, 'wb') as raw, gzip.GzipFile(fileobj=raw, mode='wb', mtime=0) as stream:
                with source.cursor(pymysql.cursors.SSCursor) as cursor:
                    cursor.execute('SELECT '+','.join('`'+n+'`' for n in fields)+' FROM `'+table+'`'+order)
                    for row in cursor:
                        content = dumps(list(row)).encode() + b'\n'
                        digest.update(content)
                        stream.write(content)
                        count += 1
            manifest['tables'][table] = {'columns': metadata, 'count': count, 'sha256': digest.hexdigest()}
            print(table, count, flush=True)
        path = output / 'manifest.json'
        fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        with os.fdopen(fd, 'w') as stream:
            stream.write(dumps(manifest) + '\n')
    finally:
        source.rollback()
        source.close()
    print('Snapshot completed:', output)


def source_rows(directory, table, entry):
    digest = hashlib.sha256()
    count = 0
    with gzip.open(directory / (table + '.jsonl.gz'), 'rt') as stream:
        for line in stream:
            digest.update(line.encode())
            count += 1
            yield json.loads(line, object_hook=decode)
    if {'count': count, 'sha256': digest.hexdigest()} != {k: entry[k] for k in ['count','sha256']}:
        raise ValueError('Snapshot checksum mismatch: ' + table)


def convert(value, kind, udt):
    if value is None:
        return None
    if kind in ['smallint', 'integer', 'bigint']:
        return int(value)
    if kind == 'numeric':
        return decimal.Decimal(str(value))
    if kind in ['json', 'jsonb']:
        return json.loads(value, parse_float=decimal.Decimal) if isinstance(value, str) else value
    if udt == 'bpchar':
        return value.rstrip(' ')
    return value


def restore(directory):
    directory = directory.expanduser().resolve()
    manifest = json.loads((directory / 'manifest.json').read_text())
    if manifest['format'] != 1:
        raise ValueError('Unsupported snapshot format')
    expected_seed = json.loads(SEED_MANIFEST.read_text())
    with psycopg.connect(os.environ['TARGET_PG_DSN']) as connection:
        set_json_loads(lambda data: json.loads(data, parse_float=decimal.Decimal), connection)
        connection.execute("SET LOCAL lock_timeout='10s'")
        connection.execute("SET LOCAL statement_timeout='300s'")
        connection.execute("SELECT pg_advisory_xact_lock(1876323110)")
        if connection.execute("SELECT EXISTS(SELECT 1 FROM pg_namespace WHERE nspname='migration_archive')").fetchone()[0]:
            raise ValueError('Target already contains an import archive; refusing to overwrite')
        public = tables(connection)
        # 从检查到替换始终持有排他锁，拒绝覆盖已有账号、业务记录或被修改过的种子。
        connection.execute(sql.SQL('LOCK TABLE {} IN ACCESS EXCLUSIVE MODE').format(
            sql.SQL(',').join(sql.Identifier('public', t) for t in public)))
        if target_manifest(connection) != expected_seed:
            raise ValueError('Target is not the pristine PostgreSQL seed database; refusing to replace data')
        plans = {}
        for table in public:
            if table in EXCLUDED_LOG_TABLES:
                continue
            if table not in manifest['tables']:
                raise ValueError('Source missing current table: ' + table)
            available = [c['COLUMN_NAME'] for c in manifest['tables'][table]['columns']]
            plan = [(name, kind, udt, available.index(name)) for name,kind,udt,generated in columns(connection, table) if generated=='NEVER']
            plans[table] = plan
        connection.execute(sql.SQL('TRUNCATE {}').format(sql.SQL(',').join(sql.Identifier('public', t) for t in public)))
        connection.execute('CREATE SCHEMA migration_archive')
        connection.execute('REVOKE ALL ON SCHEMA migration_archive FROM PUBLIC')
        connection.execute("COMMENT ON SCHEMA migration_archive IS '数据库迁移的一次性原始快照归档；应用不读取，不恢复已删除业务'")
        connection.execute('CREATE TABLE migration_archive.source_manifest (table_name text PRIMARY KEY, metadata jsonb NOT NULL)')
        connection.execute("COMMENT ON TABLE migration_archive.source_manifest IS '原始表字段和逐表摘要'")
        connection.execute("COMMENT ON COLUMN migration_archive.source_manifest.table_name IS '原始表名'")
        connection.execute("COMMENT ON COLUMN migration_archive.source_manifest.metadata IS '原始字段定义、行数和内容摘要'")
        connection.execute('CREATE TABLE migration_archive.source_rows (table_name text NOT NULL, row_number bigint NOT NULL, row_data text NOT NULL, PRIMARY KEY(table_name,row_number))')
        connection.execute("COMMENT ON TABLE migration_archive.source_rows IS '全部源数据的无损类型标记归档，包括旧表和旧字段'")
        for col,comment in [('table_name','原始表名'),('row_number','快照内行序号'),('row_data','包含类型标记的原始整行内容')]:
            connection.execute(sql.SQL('COMMENT ON COLUMN migration_archive.source_rows.{} IS {}').format(sql.Identifier(col),sql.Literal(comment)))
        report = {}
        # 先归档，包括已经删除的业务表和 MySQL 原迁移账本。
        for table,entry in manifest['tables'].items():
            if table in EXCLUDED_LOG_TABLES:
                connection.execute('INSERT INTO migration_archive.source_manifest VALUES (%s,%s)',
                                   (table, Jsonb(entry | {'excluded': True, 'reason': 'Operational log'})))
                continue
            connection.execute('INSERT INTO migration_archive.source_manifest VALUES (%s,%s)', (table, Jsonb(entry)))
            with connection.cursor().copy('COPY migration_archive.source_rows FROM STDIN') as copy:
                for index,row in enumerate(source_rows(directory, table, entry), 1):
                    copy.write_row((table, index, dumps(row)))
            with connection.cursor(name='archive_verify_' + table) as cursor:
                cursor.itersize = 5000
                cursor.execute('SELECT row_data FROM migration_archive.source_rows WHERE table_name=%s ORDER BY row_number', (table,))
                actual = hashlib.sha256()
                count = 0
                for row in cursor:
                    actual.update(row[0].encode() + b'\n')
                    count += 1
            if count != entry['count'] or actual.hexdigest() != entry['sha256']:
                raise ValueError('Archive verification failed: ' + table)
        # user.avatar_file_id 外键要求 file 先导入。
        for table in sorted(public, key=lambda t: (t=='user',t)):
            if table in EXCLUDED_LOG_TABLES:
                continue
            entry = manifest['tables'][table]
            plan = plans[table]
            expected = hashlib.sha256()
            count = 0
            query = sql.SQL('COPY public.{} ({}) FROM STDIN').format(sql.Identifier(table),sql.SQL(',').join(sql.Identifier(c[0]) for c in plan))
            with connection.cursor().copy(query) as copy:
                for row in source_rows(directory, table, entry):
                    values = [convert(row[index],kind,udt) for _,kind,udt,index in plan]
                    expected.update(dumps(canonical(values)).encode()+b'\n')
                    # COPY 输入保留 JSON 原始数字精度，由 PostgreSQL 解析。
                    copy.write_row([row[index] if kind in ['json','jsonb'] else value
                                    for (_,kind,udt,index),value in zip(plan,values)])
                    count += 1
            actual = hash_rows(pg_rows(connection, table, [c[0] for c in plan]))
            if actual != {'count':count,'sha256':expected.hexdigest()}:
                raise ValueError('Current table verification failed: '+table)
            report[table] = actual
            print('VERIFIED',table,count,flush=True)
        connection.execute('SET CONSTRAINTS ALL IMMEDIATE')
        archive_count = connection.execute('SELECT count(*) FROM migration_archive.source_rows').fetchone()[0]
        if archive_count != sum(t['count'] for name,t in manifest['tables'].items() if name not in EXCLUDED_LOG_TABLES):
            raise ValueError('Archive row total mismatch')
        # 所有数据与核对记录一起提交；任何错误回滚整个导入。
        connection.execute('INSERT INTO migration_archive.source_manifest VALUES (%s,%s)',
                           ('__verified_import__',Jsonb({'tables':report,'archiveRows':archive_count,'excludedLogs':sorted(EXCLUDED_LOG_TABLES)})))
    print('COMMITTED',len(report),'current tables;',archive_count,'archived source rows')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['snapshot','restore','seed-manifest'])
    parser.add_argument('directory', type=Path)
    args = parser.parse_args()
    if args.action == 'snapshot':
        snapshot(args.directory)
    elif args.action == 'restore':
        restore(args.directory)
    else:
        with psycopg.connect(os.environ['TARGET_PG_DSN']) as connection:
            set_json_loads(lambda data: json.loads(data, parse_float=decimal.Decimal), connection)
            connection.read_only = True
            args.directory.write_text(dumps(target_manifest(connection))+'\n')


if __name__ == '__main__':
    try:
        main()
    except psycopg.Error as error:
        # 驱动错误可能包含 COPY 整行数据，绝不输出异常正文或堆栈。
        raise SystemExit('PostgreSQL migration failed; transaction rolled back; SQLSTATE=' + str(error.sqlstate)) from None
