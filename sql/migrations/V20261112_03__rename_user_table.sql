-- 目的：将用户主表 "user" 重命名为 sys_user，避免 PostgreSQL 保留字造成的强制引用。
-- 前置：PostgreSQL 18+，已执行 V20261110_01 结构初始化及 V20261112_01、V20261112_02 后续适用增量。
-- 影响：仅重命名表、主键/检查约束和索引，不改列、索引列与业务数据；应用实体与手写 SQL 同步改用 sys_user。
-- 重跑：由 Flyway 版本历史保证只执行一次；目标表已存在时迁移失败并回滚，修正原因后重新迁移。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；失败事务回滚。
ALTER TABLE "user" RENAME TO sys_user;
ALTER TABLE sys_user RENAME CONSTRAINT "user_pkey" TO "sys_user_pkey";
ALTER TABLE sys_user RENAME CONSTRAINT "ck_user_phone_pair" TO "ck_sys_user_phone_pair";
ALTER INDEX "user_idx_user_avatar_file" RENAME TO "sys_user_idx_user_avatar_file";
ALTER INDEX "user_idx_user_erasure_retry" RENAME TO "sys_user_idx_user_erasure_retry";
ALTER INDEX "user_uk_user_phone_active" RENAME TO "sys_user_uk_user_phone_active";
ALTER INDEX "user_uk_user_wechat_active" RENAME TO "sys_user_uk_user_wechat_active";
ALTER INDEX "uk_user_username_active" RENAME TO "sys_user_uk_user_username_active";
ALTER INDEX "uk_user_email_active" RENAME TO "sys_user_uk_user_email_active";
