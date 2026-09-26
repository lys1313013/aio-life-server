#!/usr/bin/env python3
"""从已审阅的监管名录快照生成银行及菜单初始化 SQL；不连接数据库，不请求外部服务。"""
import csv
import hashlib
import json
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'sql/reference/banks/nfra-2025-12-31.csv'
TARGET = ROOT / 'sql/2_ini_data/2026-09-26_bank_card.sql'
# 兼容最初 15 家的字典键值、显示名称及排序，不改变已有 bank_card.bank_id。
LEGACY = {
    'B0001H111000001': ('ICBC', '中国工商银行', 0),
    'B0002H111000001': ('ABC', '中国农业银行', 1),
    'B0003H111000001': ('BOC', '中国银行', 2),
    'B0004H111000001': ('CCB', '中国建设银行', 3),
    'B0005H131000001': ('BOCOM', '交通银行', 4),
    'B0018H111000001': ('PSBC', '中国邮政储蓄银行', 5),
    'B0011H144030001': ('CMB', '招商银行', 6),
    'B0006H111000001': ('CITIC', '中信银行', 7),
    'B0007H111000001': ('CEB', '中国光大银行', 8),
    'B0009H111000001': ('CMBC', '中国民生银行', 9),
    'B0013H135010001': ('CIB', '兴业银行', 10),
    'B0015H131000001': ('SPDB', '浦发银行', 11),
    'B0014H144030001': ('PAB', '平安银行', 12),
    'B0012H144010001': ('CGB', '广发银行', 13),
    'B0008H111000001': ('HXB', '华夏银行', 14),
}


def quote(value):
    assert '\\' not in value and '\x00' not in value
    return "'" + value.replace("'", "''") + "'"


def main():
    metadata = json.loads(SOURCE.with_suffix('.metadata.json').read_text())
    assert hashlib.sha256(SOURCE.read_bytes()).hexdigest() == metadata['csv_sha256']
    with SOURCE.open() as stream:
        rows = list(csv.DictReader(stream))
    assert Counter(row['institution_type'] for row in rows) == metadata['included_counts']
    assert len(rows) == 3132
    assert len({row['institution_code'] for row in rows}) == len(rows)
    assert len({row['name'] for row in rows}) == len(rows)
    assert set(LEGACY) <= {row['institution_code'] for row in rows}
    ids, values, labels, records = set(), set(), set(), []
    for index, row in enumerate(rows):
        code, name = row['institution_code'], row['name']
        assert re.fullmatch(r'[A-Z][0-9]{4}[A-Z][0-9]{9}', code), code
        assert 1 <= int(row['page']) <= 259
        label = re.sub(r'(股份有限公司|有限责任公司|有限公司)$', '', name)
        value, label, order = LEGACY.get(code, ('NFRA_' + code, label, 100 + index))
        # 由机构编码派生稳定 ID；不受 CSV 排序变化影响，冲突直接报错，不静默覆盖。
        row_id = (92626101 + order) if code in LEGACY else (
            100_000_000_000_000 + int(hashlib.sha256(code.encode()).hexdigest()[:12], 16)
        )
        assert row_id not in ids and value not in values and label not in labels
        ids.add(row_id)
        values.add(value)
        labels.add(label)
        remark = f"国家金融监督管理总局；截至2025-12-31；{code}；{row['institution_type']}；{name}；PDF第{row['page']}页"
        assert len(label) <= 100 and len(value) <= 100 and len(remark) <= 500
        records.append(f"({row_id},{order},{quote(label)},{quote(value)},{quote(name)},{quote(remark)})")
    header = """-- 由 scripts/generate_bank_dictionary.py 生成，请勿手工修改。
-- 来源：国家金融监督管理总局，截至 2025-12-31，发布于 2026-08-14。
-- 3132 家大陆银行/农信法人机构；不是发卡资格、卡种或港澳台全量名录。
-- 来源、哈希及筛选规则见 sql/reference/banks/README.md。
-- 包含银行字典类型、全部3132家机构和银行卡菜单；重复执行保留已有配置。
-- 在无并发字典维护的迁移窗口执行；主键冲突直接失败，禁止使用 INSERT IGNORE。
USE `aio_life`;
INSERT INTO sys_dict_type(dict_id,dict_name,dict_type,status,is_deleted,create_user,update_user)
SELECT 92626001,'银行','bank','0',0,0,0
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_type WHERE dict_type='bank' AND is_deleted=0);
SET @bank_dict_id = (SELECT dict_id FROM sys_dict_type WHERE dict_type='bank' AND is_deleted=0);
DROP TEMPORARY TABLE IF EXISTS tmp_nfra_bank_dictionary;
CREATE TEMPORARY TABLE tmp_nfra_bank_dictionary (
  seed_id BIGINT PRIMARY KEY,
  sort_order INT NOT NULL,
  label VARCHAR(100) NOT NULL,
  bank_value VARCHAR(100) NOT NULL UNIQUE,
  full_name VARCHAR(100) NOT NULL,
  source_remark VARCHAR(500) NOT NULL
);
"""
    body = []
    for start in range(0, len(records), 200):
        body.append('INSERT INTO tmp_nfra_bank_dictionary(seed_id,sort_order,label,bank_value,full_name,source_remark) VALUES\n'
                    + ',\n'.join(records[start:start + 200]) + ';\n')
    footer = """
INSERT INTO sys_dict_data(dict_code,dict_id,dict_sort,dict_label,dict_value,status,is_deleted,create_user,update_user,remark)
SELECT s.seed_id,@bank_dict_id,s.sort_order,s.label,s.bank_value,'0',0,0,0,s.source_remark
FROM tmp_nfra_bank_dictionary s
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_data d WHERE d.dict_id=@bank_dict_id
  AND (d.dict_value=s.bank_value OR d.dict_label=s.label OR d.dict_label=s.full_name)
);
DROP TEMPORARY TABLE tmp_nfra_bank_dictionary;

INSERT INTO sys_menu(id,parent_id,name,path,component,meta,sort,status,is_deleted,create_user,update_user)
SELECT 1605,1600,'bankCards','/finance/bank-cards','bank-card/index','{"icon":"lucide:credit-card","title":"银行卡","keepAlive":false}',4,1,0,0,0
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE path='/finance/bank-cards' AND is_deleted=0);
"""
    TARGET.write_text(header + '\n'.join(body) + footer)
    print(f'Generated {TARGET.name}: {len(rows)} institutions and bank card menu')


if __name__ == '__main__':
    main()
