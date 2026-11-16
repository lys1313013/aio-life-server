-- 目的：删除 sys_dict_data.dict_type；字典项所属类型只由 dict_id 关联 sys_dict_type 表达，不再另存一份。
-- 前置：PostgreSQL 18+；已执行 V20261116_03，sys_dict_data 为当前字典结构。
-- 影响：仅删除该冗余列及其历史取值，不回填数据；保留 dict_id 关联、字典项、标签、键值、启用状态与 uk_sys_dict_data_dict_id_value。
-- 重跑：Flyway 成功后不重跑；手工重复执行时列不存在会跳过，其他结构错误仍失败。
-- 恢复：事务内失败自动回滚；成功后若需恢复历史取值，须使用迁移前备份。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate，遇错停止。
-- 验证：查询 information_schema.columns 确认列不存在，并核对 sys_dict_data 行数与银行字典查询结果和迁移前一致。

ALTER TABLE sys_dict_data DROP COLUMN IF EXISTS dict_type;
