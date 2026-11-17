-- 目的：删除 cbti_personality.image_object；人格图片统一由 attachment（biz_type=cbti_personality_image）表达，该列已无读写方。
-- 前置：PostgreSQL 18+；已执行 V20261118_06，cbti_personality 含 image_object 列。
-- 影响：仅删除该列及其历史对象路径，不改行数、其他列、索引和约束；新库由 V20261110_02_001 写入初始值后在本迁移删除。
-- 重跑：Flyway 成功后不重跑；手工重复执行时列不存在会跳过，其他结构错误仍失败。
-- 恢复：事务内失败自动回滚；成功后若需恢复该列，须使用迁移前备份或编写新的前向迁移。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate，遇错停止。
-- 验证：查询 information_schema.columns 确认列不存在，并核对 cbti_personality 行数与人格图片附件关联和迁移前一致。

ALTER TABLE cbti_personality DROP COLUMN IF EXISTS image_object;