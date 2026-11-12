-- 目的：移除时迹分类的“必须／积极／休闲”时间类型。
-- 前置：PostgreSQL 18+；已执行 V20261112_13，time_tracker_category 为当前分类结构。
-- 影响：仅删除 time_type 列及其历史取值；保留分类、覆盖关系、层级、统计开关和全部时迹记录，不回填数据。
-- 重跑：Flyway 成功后不重跑；手工重复执行时列不存在会跳过，其他结构错误仍失败。
-- 恢复：事务内失败自动回滚；成功后若需恢复已删除的类型取值，须使用迁移前备份。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate，遇错停止。
-- 验证：查询 information_schema.columns 确认列不存在，并核对迁移前后的分类及记录数与关联关系。

ALTER TABLE time_tracker_category DROP COLUMN IF EXISTS time_type;
