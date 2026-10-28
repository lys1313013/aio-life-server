#!/usr/bin/env python3
"""Verify comment-only migration in owned, disposable MySQL 5.7/8 containers.

Never reads business credentials or connects to a network/database supplied by the caller.
Docker images must already be available locally. Synthetic rows only.
"""
from pathlib import Path
import json
import re
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
MIGRATION = ROOT / 'sql/migrations/2026-10-28_04_schema_comments.sql'
SOURCE = ROOT / 'sql/1_init_table/2026-08-18_init_all_tables.sql'
SQL_STRING = r"'((?:''|[^'])*)'"
source = MIGRATION.read_text()
column_plan = re.search(r'INSERT INTO `_aio_sc_columns`.*?VALUES\n(.*?);\n  INSERT', source, re.S)[1]
columns = [tuple(x.replace("''", "'") for x in row) for row in
           re.findall(r'\(' + ', '.join([SQL_STRING] * 4) + r'\)', column_plan)]
table_plan = re.search(r'INSERT INTO `_aio_sc_tables`.*?VALUES\n(.*?);\n', source, re.S)[1]
tables = [tuple(x.replace("''", "'") for x in row) for row in
          re.findall(r'\(' + ', '.join([SQL_STRING] * 2) + r'\)', table_plan)]
assert len(columns) == 73 and len(tables) == 7


def quote(value):
    return "'" + value.replace('\\', '\\\\').replace("'", "''") + "'"


def run(command, **kwargs):
    result = subprocess.run(command, capture_output=True, text=True, timeout=120, **kwargs)
    if result.returncode:
        raise RuntimeError(result.stderr)
    return result.stdout


def test(image):
    name = 'aio-comments-' + uuid.uuid4().hex[:10]
    run(['docker', 'run', '--detach', '--rm', '--network', 'none', '--tmpfs', '/var/lib/mysql',
         '--name', name, '-e', 'MYSQL_ALLOW_EMPTY_PASSWORD=yes', image])
    command = ['docker', 'exec', '-i', name, 'mysql', '--no-defaults', '-uroot',
               '--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names']

    def sql(statement, db='comments_test'):
        return run(command + ([db] if db else []), input=statement).strip()

    def rows(statement):
        return [json.loads(line) for line in sql(statement).splitlines() if line]

    def metadata():
        return rows("""SELECT JSON_ARRAY(table_name,column_name,column_type,is_nullable,column_default,
            extra,generation_expression,character_set_name,collation_name,ordinal_position)
            FROM information_schema.columns WHERE table_schema=DATABASE()
            ORDER BY table_name,ordinal_position""")

    def comments():
        return {(t, c): value for t, c, value in rows("""SELECT JSON_ARRAY(table_name,column_name,column_comment)
            FROM information_schema.columns WHERE table_schema=DATABASE()""")}

    def indexes():
        return rows("""SELECT JSON_ARRAY(table_name,index_name,non_unique,seq_in_index,column_name,sub_part,index_type)
            FROM information_schema.statistics WHERE table_schema=DATABASE()
            ORDER BY table_name,index_name,seq_in_index""")

    def table_metadata():
        return rows("""SELECT JSON_ARRAY(table_name,engine,table_collation,row_format,create_options)
            FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name""")

    def data():
        return {table: sql('SELECT * FROM `' + table + '` ORDER BY 1') for table in sorted({x[0] for x in columns})}

    try:
        for _ in range(60):
            try:
                if sql('SELECT @@GLOBAL.skip_networking', None) == '0':
                    break
                time.sleep(1)
            except RuntimeError:
                time.sleep(1)
        else:
            raise AssertionError('Isolated MySQL did not start')
        sql('CREATE DATABASE comments_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci', None)
        ddl = SOURCE.read_text()
        ddl = re.sub(r'(?im)^\s*(?:create database if not exists|use)\s+`?aio_life`?\s*;\s*$', '', ddl)
        if image.startswith('mysql:5.'):
            # 5.7 accepts anonymous CHECK syntax but does not enforce it; migration itself is unmodified.
            ddl = re.sub(r'CONSTRAINT\s+`[^`]+`\s+CHECK', 'CHECK', ddl, flags=re.I)
        sql(ddl)
        # Exercise attributes that comment-only ALTER must not replace with baseline defaults.
        sql("ALTER TABLE device MODIFY COLUMN spec VARCHAR(257) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin "
            "NULL DEFAULT " + quote("quoted'path\\value") + " COMMENT 'old';"
            "ALTER TABLE b_video MODIFY COLUMN last_watched DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) "
            "ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'old';"
            "ALTER TABLE user_dict_data MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'old';"
            "ALTER TABLE record_article MODIFY COLUMN is_deleted TINYINT NULL DEFAULT 0 COMMENT 'old';")
        # Strip only comments using SHOW CREATE TABLE definitions, preserving all existing attributes.
        for table in sorted({x[0] for x in columns}):
            ddl = sql('SHOW CREATE TABLE `' + table + '`').split('\t', 1)[1]
            changes = []
            for t, column, _, _ in columns:
                if t != table:
                    continue
                line = re.search(r'^\s*`' + re.escape(column) + r'` .+$', ddl, re.M)[0].strip().rstrip(',')
                line = re.sub(r" COMMENT '(?:\\.|''|[^'])*'", '', line)
                changes.append('MODIFY COLUMN ' + line + " COMMENT 'old'")
            sql('ALTER TABLE `' + table + '` ' + ', '.join(changes))
            info = rows("SELECT JSON_ARRAY(column_name,data_type,is_nullable,column_default,extra) "
                        "FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=" + quote(table))
            values = {}
            for column, dtype, nullable, default, extra in info:
                if nullable != 'NO' or default is not None or 'auto_increment' in extra:
                    continue
                if dtype in ('tinyint','smallint','mediumint','int','bigint','decimal','double','float'):
                    values[column] = '1'
                elif dtype in ('datetime','timestamp'):
                    values[column] = "'2026-01-01 01:02:03'"
                elif dtype == 'date':
                    values[column] = "'2026-01-01'"
                elif dtype == 'json':
                    values[column] = "'{}'"
                else:
                    values[column] = "'synthetic'"
            sql('INSERT INTO `' + table + '` (' + ','.join('`'+c+'`' for c in values) + ') VALUES (' + ','.join(values.values()) + ')')
        for table, _ in tables:
            sql('ALTER TABLE `' + table + "` COMMENT='old'")
        before = metadata(), indexes(), table_metadata(), data()
        # Fixed procedure sql_mode must handle QUOTE despite caller NO_BACKSLASH_ESCAPES.
        result = sql("SET SESSION sql_mode='NO_BACKSLASH_ESCAPES';\n" + source + '\nSELECT @@SESSION.sql_mode;')
        assert result == 'NO_BACKSLASH_ESCAPES', result
        assert (metadata(), indexes(), table_metadata(), data()) == before, 'Non-comment attribute or data changed'
        actual = comments()
        for table, column, _, expected in columns:
            assert actual[(table, column)] == expected, (table, column)
        actual_tables = dict(rows('SELECT JSON_ARRAY(table_name,table_comment) FROM information_schema.tables WHERE table_schema=DATABASE()'))
        for table, expected in tables:
            assert actual_tables[table] == expected, table
        stable = metadata(), indexes(), table_metadata(), data(), comments()
        sql(source)
        assert (metadata(), indexes(), table_metadata(), data(), comments()) == stable, 'Repeat changed state'
        # Missing late column and unexpected type must fail before touching an earlier table.
        sql("ALTER TABLE b_video MODIFY COLUMN last_watched DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) "
            "ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'pending'; ALTER TABLE user_dict_type DROP COLUMN color")
        try:
            sql(source)
        except RuntimeError as error:
            assert 'Missing column' in str(error), error
        else:
            raise AssertionError('Missing column was accepted')
        assert comments()[('b_video', 'last_watched')] == 'pending', 'Preflight applied earlier ALTER'
        sql("ALTER TABLE user_dict_type ADD COLUMN color INT NULL COMMENT 'conflict'")
        try:
            sql(source)
        except RuntimeError as error:
            assert 'Unsupported column definition' in str(error), error
        else:
            raise AssertionError('Unexpected type was accepted')
        assert comments()[('b_video', 'last_watched')] == 'pending'
        sql("ALTER TABLE user_dict_type MODIFY COLUMN color VARCHAR(20) NULL COMMENT 'fixed'")
        sql(source)
        assert comments()[('b_video', 'last_watched')] != 'pending', 'Recovery did not finish'
        sql("ALTER TABLE b_video MODIFY COLUMN last_watched DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) "
            "ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'pending-generated';"
            "ALTER TABLE device DROP COLUMN spec, ADD COLUMN spec VARCHAR(257) GENERATED ALWAYS AS (name) STORED")
        try:
            sql(source)
        except RuntimeError as error:
            assert 'Unsupported column definition' in str(error), error
        else:
            raise AssertionError('Generated column was accepted')
        assert comments()[('b_video', 'last_watched')] == 'pending-generated'
        sql("ALTER TABLE device DROP COLUMN spec, ADD COLUMN spec VARCHAR(257) NULL; DROP TABLE message")
        try:
            sql(source)
        except RuntimeError as error:
            assert 'Missing or non-InnoDB base table' in str(error), error
        else:
            raise AssertionError('Missing table-only comment target was accepted')
        assert comments()[('b_video', 'last_watched')] == 'pending-generated'
        print(image + ': PASS — 73 fields/7 tables; data/indexes/all non-comment attributes retained; repeat, partial recovery, missing/type/generated preflight', flush=True)
    finally:
        run(['docker', 'stop', name])


if __name__ == '__main__':
    for image in ('mysql:5.7.44', 'mysql:8.0.40'):
        test(image)
