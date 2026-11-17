-- 目的：删除 sys_scheduled_job.zone_id；调度时区固定为 Asia/Shanghai，只由代码中的 ScheduledJobRules 维护。
-- 前置：PostgreSQL 18+；已执行 V20261118_04，sys_scheduled_job 含 zone_id 列。
-- 影响：仅删除该列及其历史取值，不回填数据；保留任务编码、名称、cron、启用状态、revision、审计字段和执行日志。
-- 重跑：Flyway 成功后不重跑；手工重复执行时列不存在会跳过，其他结构错误仍失败。
-- 恢复：事务内失败自动回滚；成功后若需恢复该列，须使用迁移前备份或编写新的前向迁移。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate，遇错停止。
-- 验证：查询 information_schema.columns 确认列不存在，并核对 sys_scheduled_job 行数与任务列表、调度时间计算和迁移前一致。

ALTER TABLE sys_scheduled_job DROP COLUMN IF EXISTS zone_id;