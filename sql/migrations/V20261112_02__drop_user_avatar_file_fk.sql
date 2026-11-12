-- 目的：移除 user.avatar_file_id 到 file.id 的外键，头像引用统一由应用层校验。
-- 前置：PostgreSQL 18+，已执行 V20261110_01 结构初始化及后续适用版本。
-- 影响：只删除约束 fk_user_avatar_file；不改列定义、索引和业务数据，不删除头像文件记录。
-- 重跑：约束已不存在时跳过，可重复执行。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；失败事务回滚。
ALTER TABLE "user" DROP CONSTRAINT IF EXISTS "fk_user_avatar_file";
