#!/usr/bin/env python3
"""Verify the field migration using MySQL containers, only in owned random scratch databases.
Requires docker exec access and root access without password in isolated test containers.
"""
from pathlib import Path
import re,subprocess,json,sys,uuid
root=Path(__file__).resolve().parents[1]
source=(root/'sql/1_init_table/2026-08-18_init_all_tables.sql').read_text()
migration=(root/'sql/migrations/2026-10-28_02_unify_common_fields.sql').read_text()
plans=re.findall(r"CALL aio_unify_field\('([^']+)', '([^']+)', '([^']+)', '([^']+)', ([01]), 0\)",migration)
database=None
def sql(c,s):
 r=subprocess.run(['docker','exec','-i',c,'mysql','-uroot','--batch','--raw','--skip-column-names','--default-character-set=utf8mb4']+([database] if database else []),input=s,text=True,capture_output=True)
 if r.returncode:raise RuntimeError(r.stderr)
 return r.stdout.strip()
if len(sys.argv)<2:
 raise SystemExit('Usage: python3 scripts/test-common-field-migration.py <isolated-mysql-container> [...]')
for c in sys.argv[1:]:
 database=None
 database_name='aio_field_test_'+uuid.uuid4().hex[:12]
 sql(c,'CREATE DATABASE `'+database_name+'` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
 database=database_name
 try:
  for t in dict.fromkeys(p[0] for p in plans):
   ddl=re.search(r'CREATE TABLE IF NOT EXISTS `'+t+r'`.*?;',source,re.S)[0]
   sql(c,'DROP TABLE IF EXISTS `'+t+'`;'+ddl)
   info=[json.loads(l) for l in sql(c,"SELECT JSON_ARRAY(column_name,data_type,is_nullable,column_default,extra) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='"+t+"' ORDER BY ordinal_position").splitlines()]
   d={k:('10' if typ in ['bigint','int','tinyint','decimal','double','float'] else "'2026-10-01'" if typ=='date' else "'2026-10-01 01:02:03'" if typ in ['datetime','timestamp'] else "'测试'") for k,typ,nullable,default,extra in info if nullable=='NO' and default is None and not extra}
   for _,o,n,comment,rev in (p for p in plans if p[0]==t):d[o]="'0'" if rev=='1' else "'迁移备注：保留内容'" if n=='remark' else '0' if 'enabled' in n else '-7'
   d['update_time']="'2026-10-01 01:02:03'"
   sql(c,'INSERT INTO `'+t+'` ('+','.join('`'+k+'`' for k in d)+') VALUES ('+','.join(d.values())+');')
   if t in ['sys_dict_data','sys_dict_type','user_dict_data','user_dict_type']:
    for i,v in enumerate(["'1'",'NULL']):
     for k,typ,nullable,default,extra in info:
      if k in ['id','dict_code','dict_id']:d[k]=str(1002+i)
      if k in ['dict_type','dict_name','dict_value','dict_label'] and k in d:d[k]="'测试"+str(i)+"'"
     d['status']=v
     sql(c,'INSERT INTO `'+t+'` ('+','.join('`'+k+'`' for k in d)+') VALUES ('+','.join(d.values())+');')
  index_query="SELECT JSON_ARRAY(table_name,index_name,non_unique,seq_in_index,column_name,sub_part,index_type) FROM information_schema.statistics WHERE table_schema=DATABASE() ORDER BY table_name,index_name,seq_in_index"
  before_indexes=[json.loads(line) for line in sql(c,index_query).splitlines()]
  for row in before_indexes:
   for t,o,n,comment,rev in plans:
    if row[0]==t and row[4]==o:row[4]=n
  sql(c,migration)
  for t,o,n,comment,rev in plans:
   if rev=='1': assert sql(c,'SELECT GROUP_CONCAT(COALESCE(is_enabled,\'NULL\') ORDER BY COALESCE(is_enabled,-1)) FROM `'+t+'`')=='NULL,0,1',t
   else:
    expected_value='迁移备注：保留内容' if n=='remark' else '0' if 'enabled' in n else '-7'
    assert sql(c,'SELECT DISTINCT `'+n+'` FROM `'+t+'`')==expected_value,(t,n)
   assert sql(c,'SELECT DISTINCT update_time FROM `'+t+'`')=='2026-10-01 01:02:03',t
  sql(c,migration)
  assert [json.loads(line) for line in sql(c,index_query).splitlines()]==before_indexes
  assert sql(c,"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name='__aio_legacy_status'")=='0'
  # Dictionary conversion interrupted before backfill must resume, preserving NULL and audit time.
  snapshot=sql(c,'SELECT id,is_enabled,update_time FROM user_dict_data ORDER BY id')
  sql(c,"ALTER TABLE user_dict_data CHANGE COLUMN is_enabled __aio_legacy_status CHAR(1) NULL DEFAULT '0', ADD COLUMN is_enabled TINYINT NULL COMMENT '字段统一迁移中，禁止业务写入'; UPDATE user_dict_data SET __aio_legacy_status=CASE __aio_legacy_status WHEN '1' THEN '0' WHEN '0' THEN '1' ELSE NULL END,update_time=update_time")
  sql(c,migration)
  assert sql(c,'SELECT id,is_enabled,update_time FROM user_dict_data ORDER BY id')==snapshot
  # Invalid old flag prevents even the earlier menu rename from being applied.
  sql(c,"ALTER TABLE sys_menu CHANGE COLUMN sort_order sort INT NOT NULL DEFAULT 0 COMMENT '排序'; ALTER TABLE sys_dict_type CHANGE COLUMN is_enabled status CHAR(1) NULL DEFAULT '0'; UPDATE sys_dict_type SET status='2',update_time=update_time")
  try:sql(c,migration)
  except RuntimeError as error:assert '45000' in str(error)
  else:raise AssertionError('invalid dictionary value was accepted')
  assert sql(c,"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_menu' AND column_name='sort'")=='1'
  sql(c,"UPDATE sys_dict_type SET status='0',update_time=update_time")
  sql(c,migration)
  # Completed dictionary columns still reject values outside the current 0/1 contract.
  sql(c,'UPDATE sys_dict_type SET is_enabled=2,update_time=update_time')
  try:sql(c,migration)
  except RuntimeError as error:assert '45000' in str(error)
  else:raise AssertionError('invalid migrated dictionary value was accepted')
  sql(c,'UPDATE sys_dict_type SET is_enabled=1,update_time=update_time')
  sql(c,migration)
  # A conflicting old/new column pair must stop instead of silently selecting one.
  sql(c,"ALTER TABLE sys_menu ADD COLUMN sort INT COMMENT '冲突测试'")
  try:sql(c,migration)
  except RuntimeError as error:assert '45000' in str(error)
  else:raise AssertionError('conflicting names were accepted')
  sql(c,'ALTER TABLE sys_menu DROP COLUMN sort')
  sql(c,migration)
  print(c,'PASS: 23 columns/18 tables; data, indexes and audit retained; repeat/resume/conflict/invalid preflight',flush=True)
 finally:
  database=None
  sql(c,'DROP DATABASE `'+database_name+'`')
