#!/usr/bin/env python3
"""Validate 01/03 migrations in owned, network-isolated disposable MySQL containers.

Never accepts business database credentials or an existing container. Docker images
must already be available locally. No host ports or persistent data volumes are used.
"""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import argparse
import json
import re
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
BASELINE = (ROOT / 'sql/1_init_table/2026-08-18_init_all_tables.sql').read_text()
ASSET = ROOT / 'sql/migrations/2026-10-28_01_asset_purchase_source.sql'
ALIGNMENT = ROOT / 'sql/migrations/2026-10-28_03_schema_alignment.sql'
TABLES = ('device', 'wardrobe_item', 'expense', 'income', 'user_dict_data', 'user_dict_type', 'login_log', 'mail_log')
SOURCE_COLUMNS = ('source_platform', 'source_order_id', 'source_product_id', 'source_item_id', 'source_unit_index')


class Scratch:
    def __init__(self, image):
        self.image = image
        self.name = 'aio-schema-alignment-' + uuid.uuid4().hex[:12]
        self.database = 'aio_migration_' + uuid.uuid4().hex[:12]
        self.checks = []

    def sql(self, statement, database=True):
        command = ['docker', 'exec', '-i', self.name, 'mysql', '--no-defaults', '-uroot',
                   '--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names']
        if database:
            command.append(self.database)
        result = subprocess.run(command, input=statement, text=True, capture_output=True, timeout=90)
        if result.returncode:
            raise RuntimeError(result.stderr.strip())
        return result.stdout.strip()

    def __enter__(self):
        subprocess.run(['docker', 'image', 'inspect', self.image], check=True, capture_output=True)
        result = subprocess.run(['docker', 'run', '--detach', '--rm', '--network', 'none',
                                 '--name', self.name, '--tmpfs', '/var/lib/mysql',
                                 '-e', 'MYSQL_ALLOW_EMPTY_PASSWORD=yes', self.image],
                                capture_output=True, text=True, check=True)
        try:
            for attempt in range(90):
                try:
                    logs = subprocess.run(['docker', 'logs', self.name], capture_output=True, text=True, timeout=10)
                    if 'MySQL init process done. Ready for start up.' not in logs.stdout + logs.stderr:
                        raise RuntimeError('Container initialization is still running')
                    self.sql('SELECT 1', database=False)
                    break
                except RuntimeError:
                    if attempt == 89:
                        raise
                    time.sleep(1)
        except BaseException:
            self.__exit__(None, None, None)
            raise
        return self

    def __exit__(self, *_):
        subprocess.run(['docker', 'rm', '--force', self.name], check=False, capture_output=True, timeout=30)

    def fresh(self):
        self.sql(f'DROP DATABASE IF EXISTS `{self.database}`; CREATE DATABASE `{self.database}` '
                 'CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci', database=False)
        definitions = []
        for table in TABLES:
            ddl = re.search(r'CREATE TABLE IF NOT EXISTS `' + table + r'`\s*\([\s\S]*?\) ENGINE=[^\n]+;', BASELINE)
            assert ddl, table
            definitions.append(ddl[0])
        self.sql('\n'.join(definitions))
        self.sql("INSERT INTO device(id,user_id,name,update_time) VALUES(101,11,'设备夹具','2026-01-02 03:04:05');"
                 "INSERT INTO wardrobe_item(id,user_id,name,update_time) VALUES(201,11,'衣物夹具','2026-01-02 03:04:05');"
                 "INSERT INTO expense(id,amt,exp_type_id,user_id,exp_time,update_time) VALUES(301,12.34,21,11,'2026-01-01 00:00:00','2026-01-02 03:04:05');"
                 "INSERT INTO income(id,amt,inc_type_id,user_id,inc_date,update_time) VALUES(401,56.78,22,11,'2026-01-01','2026-01-02 03:04:05');"
                 "INSERT INTO user_dict_data(id,user_id,template_id,dict_type,update_time) VALUES(501,11,31,'expense','2026-01-02 03:04:05');"
                 "INSERT INTO user_dict_type(id,user_id,dict_name,dict_type,update_time) VALUES(601,11,'中文类型','expense','2026-01-02 03:04:05');"
                 "INSERT INTO login_log(id,username,ip_address) VALUES(701,'fixture','127.0.0.1');"
                 "INSERT INTO mail_log(id,send_to,subject) VALUES(801,'fixture@example.invalid','夹具');")

    def schema(self):
        queries = [
            "SELECT JSON_ARRAY(table_name,column_name,column_type,is_nullable,column_default,extra,character_set_name,collation_name,column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY table_name,column_name",
            "SELECT JSON_ARRAY(table_name,index_name,seq_in_index,column_name,non_unique,sub_part,index_type,collation) FROM information_schema.statistics WHERE table_schema=DATABASE() ORDER BY table_name,index_name,seq_in_index",
        ]
        return [self.sql(q) for q in queries]

    def failure(self, migration, setup, expected='45000'):
        self.fresh()
        self.sql(setup)
        before = self.schema()
        try:
            self.sql(migration.read_text())
        except RuntimeError as error:
            assert expected in str(error), str(error)
        else:
            raise AssertionError('Invalid input was accepted: ' + setup)
        assert self.schema() == before, 'A rejected migration changed business schema'

    def source_count(self):
        return int(self.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
                            "AND table_name IN ('device','wardrobe_item') AND column_name LIKE 'source_%'"))

    def legacy_asset(self):
        for table in ('device', 'wardrobe_item'):
            self.sql(f'ALTER TABLE `{table}` DROP INDEX `idx_{table}_capture_order`, '
                     'DROP COLUMN source_platform, DROP COLUMN source_order_id, DROP COLUMN source_product_id')

    def legacy_alignment(self):
        self.sql("ALTER TABLE expense MODIFY exp_type_id MEDIUMTEXT NOT NULL COMMENT '支出类型ID', MODIFY user_id BIGINT NULL COMMENT '用户id';"
                 "ALTER TABLE income MODIFY inc_type_id MEDIUMTEXT NOT NULL COMMENT '收入类型', MODIFY amt DECIMAL(10,2) NULL COMMENT '收入';"
                 "ALTER TABLE user_dict_data DROP INDEX idx_user_dict_type, DROP INDEX idx_user_dict_template;"
                 "ALTER TABLE user_dict_type DROP INDEX idx_user_dict_type_user, MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',"
                 " MODIFY dict_name VARCHAR(100) NULL DEFAULT '' COMMENT '字典名称', MODIFY dict_type VARCHAR(100) NULL DEFAULT '' COMMENT '字典类型';"
                 "ALTER TABLE login_log ADD COLUMN password VARCHAR(255) NULL COMMENT '废弃密码';"
                 "ALTER TABLE mail_log ADD COLUMN content TEXT NULL COMMENT '废弃正文';"
                 "UPDATE login_log SET password='synthetic-obsolete'; UPDATE mail_log SET content='synthetic-obsolete';")

    def data(self):
        return self.sql("SELECT JSON_ARRAY('expense',id,CAST(exp_type_id AS CHAR),user_id,amt,update_time) FROM expense UNION ALL "
                        "SELECT JSON_ARRAY('income',id,CAST(inc_type_id AS CHAR),user_id,amt,update_time) FROM income UNION ALL "
                        "SELECT JSON_ARRAY('dictionary',id,dict_type,user_id,dict_name,update_time) FROM user_dict_type")


def asset_cases(db):
    db.fresh()
    db.sql(ASSET.read_text())
    assert db.source_count() == 10
    expected = db.schema()
    db.sql(ASSET.read_text())
    assert db.schema() == expected
    assert db.sql("SELECT COUNT(*) FROM device WHERE source_item_id IS NULL AND source_unit_index IS NULL") == '1'
    db.checks.append('asset: baseline3 -> complete5, repeat, NULL history')

    db.fresh()
    db.legacy_asset()
    db.sql(ASSET.read_text())
    assert db.source_count() == 10
    assert db.schema() == expected
    db.checks.append('asset: old0 -> complete5')

    # Mixed states model an interrupted migration and non-prefix subsets of the columns.
    db.fresh()
    db.sql(ASSET.read_text())
    db.sql('ALTER TABLE device DROP INDEX idx_device_capture_order, DROP COLUMN source_order_id, DROP COLUMN source_unit_index;'
           'ALTER TABLE wardrobe_item DROP COLUMN source_item_id;')
    db.sql(ASSET.read_text())
    assert db.schema() == expected
    db.checks.append('asset: mixed partial columns/index resume')

    db.fresh()
    db.legacy_asset()
    db.sql("ALTER TABLE wardrobe_item DROP INDEX idx_user_id, MODIFY user_id MEDIUMTEXT NOT NULL COMMENT '用户ID'")
    before = db.schema()
    try:
        db.sql(ASSET.read_text())
    except RuntimeError as error:
        assert '45000' in str(error), str(error)
    else:
        raise AssertionError('Unindexable owner column accepted')
    assert db.schema() == before, 'Second-table type conflict changed first table'
    db.checks.append('asset: second-table unindexable owner rejects before first ALTER')

    # A conflict on the second table must prevent additions to the first table.
    failures = [
        "ALTER TABLE wardrobe_item MODIFY source_product_id VARCHAR(99) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '来源商品ID，采集行sku原始字符串；无采集来源时为NULL'",
        "ALTER TABLE wardrobe_item ALTER source_order_id SET DEFAULT 'bad'",
        "ALTER TABLE wardrobe_item MODIFY source_platform VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '采集平台：jd=京东、taobao=淘宝；手工录入或历史记录为NULL'",
        "ALTER TABLE wardrobe_item MODIFY source_order_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_general_ci NULL COMMENT '来源订单ID，原始字符串；无采集来源时为NULL'",
        "ALTER TABLE wardrobe_item MODIFY source_product_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'conflict'",
        "ALTER TABLE wardrobe_item DROP INDEX idx_wardrobe_item_capture_order, ADD INDEX idx_wardrobe_item_capture_order(user_id,source_order_id)",
        "DROP TABLE wardrobe_item",
    ]
    for setup in failures:
        # NOT NULL shape fixture must first have a value, without affecting test output.
        if 'NOT NULL' in setup:
            setup = "UPDATE wardrobe_item SET source_platform='jd';" + setup
        db.failure(ASSET, setup)
    db.checks.append('asset: type/default/nullability/collation/comment/index/missing-table rejection before ALTER')
    if db.image.startswith('mysql:8'):
        db.failure(ASSET, 'ALTER TABLE wardrobe_item ALTER INDEX idx_wardrobe_item_capture_order INVISIBLE')
        db.checks.append('asset: invisible index rejected')


def alignment_cases(db):
    db.fresh()
    original = db.data()
    db.sql(ALIGNMENT.read_text())
    expected = db.schema()
    db.sql(ALIGNMENT.read_text())
    assert db.schema() == expected
    assert db.data() == original
    db.checks.append('alignment: current schema repeat/data retained')

    db.fresh()
    db.legacy_alignment()
    original = db.data()
    db.sql(ALIGNMENT.read_text())
    assert db.schema() == expected
    assert db.data() == original
    assert db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND "
                  "((table_name='login_log' AND column_name='password') OR (table_name='mail_log' AND column_name='content'))") == '0'
    db.sql(ALIGNMENT.read_text())
    assert db.schema() == expected
    db.checks.append('alignment: legacy conversion/indexes/obsolete removal/defaults/autoincrement, repeat, data retained')

    db.fresh()
    db.legacy_alignment()
    db.sql("UPDATE expense SET exp_type_id='-9223372036854775808',update_time=update_time; UPDATE income SET inc_type_id='9223372036854775807',update_time=update_time")
    original = db.data()
    db.sql(ALIGNMENT.read_text())
    assert db.data() == original
    db.checks.append('alignment: signed bigint min/max retained')

    # All failures are tested on a legacy fixture, and must precede every business ALTER.
    failures = [
        "UPDATE expense SET exp_type_id='9223372036854775808',update_time=update_time",
        "UPDATE income SET inc_type_id='-9223372036854775809',update_time=update_time",
        "UPDATE expense SET exp_type_id='12x',update_time=update_time",
        "UPDATE income SET inc_type_id='12.0',update_time=update_time",
        "UPDATE expense SET exp_type_id='',update_time=update_time",
        "UPDATE expense SET exp_type_id=' 12',update_time=update_time",
        "UPDATE expense SET exp_type_id=CONCAT('12',CHAR(10)),update_time=update_time",
        "ALTER TABLE income ADD INDEX idx_legacy_income_type(inc_type_id(10))",
        "ALTER TABLE login_log ADD COLUMN password_len INT GENERATED ALWAYS AS (CHAR_LENGTH(password)) STORED",
        "CREATE VIEW log_password_view AS SELECT password FROM login_log",
        "UPDATE expense SET user_id=NULL,update_time=update_time",
        "UPDATE income SET amt=NULL,update_time=update_time",
        "ALTER TABLE user_dict_type ADD INDEX idx_user_dict_type_user(dict_type,user_id)",
        "ALTER TABLE login_log ADD INDEX idx_obsolete_password(password)",
        "DROP TABLE mail_log",
    ]
    for setup in failures:
        db.fresh()
        db.legacy_alignment()
        db.sql(setup)
        before = db.schema()
        try:
            db.sql(ALIGNMENT.read_text())
        except RuntimeError as error:
            assert '45000' in str(error), str(error)
        else:
            raise AssertionError('Invalid input was accepted: ' + setup)
        assert db.schema() == before, 'A rejected migration changed business schema'
    db.checks.append('alignment: overflow/invalid integer/NULL/index dependency/missing-table preflight')

    db.fresh()
    db.legacy_alignment()
    db.sql("ALTER TABLE expense MODIFY exp_type_id BIGINT NOT NULL COMMENT '支出类型ID', MODIFY user_id BIGINT NOT NULL COMMENT '用户id';"
           "ALTER TABLE login_log DROP COLUMN password; ALTER TABLE user_dict_data ADD INDEX idx_user_dict_template(template_id)")
    db.sql(ALIGNMENT.read_text())
    assert db.schema() == expected
    db.checks.append('alignment: partial completion resumes')


def verify(image):
    with Scratch(image) as db:
        asset_cases(db)
        alignment_cases(db)
        result = {'image': image, 'version': db.sql('SELECT VERSION()', database=False), 'checks': db.checks, 'result': 'passed'}
        print(json.dumps(result, ensure_ascii=False), flush=True)
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--images', nargs='+', default=['mysql:5.7.44', 'mysql:8.0.40'])
    args = parser.parse_args()
    with ThreadPoolExecutor(max_workers=min(2, len(args.images))) as pool:
        list(pool.map(verify, args.images))


if __name__ == '__main__':
    main()
