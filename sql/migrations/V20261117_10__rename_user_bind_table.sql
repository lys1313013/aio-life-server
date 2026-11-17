-- 目的：把动词短语表名 user_bind 改为名词短语 user_third_party_account，明确表保存的是用户绑定的第三方平台账号。
-- 前置：PostgreSQL 18+；已执行至 V20261117_09；存在 user_bind 及其主键、唯一索引和 is_deleted 检查约束。
-- 影响：只重命名表、主键约束、检查约束和唯一索引，并同步表注释；不改列、类型、默认值、数据与接口行为。
-- 重跑：Flyway 成功后不重跑；名称已变更时重复执行会失败，需回滚后按当前结构核对，不手工补执行。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；失败事务回滚。
-- 验证：PostgresqlMigrationIT 验证空库初始化，PhysicalTableEntitySelectIntegrationTest 验证实体映射，ExportDatasetRegistryTest 验证导出目录。

ALTER TABLE "user_bind" RENAME TO "user_third_party_account";
ALTER TABLE "user_third_party_account" RENAME CONSTRAINT "user_bind_pkey" TO "user_third_party_account_pkey";
ALTER TABLE "user_third_party_account" RENAME CONSTRAINT "ck_user_bind_is_deleted" TO "ck_user_third_party_account_is_deleted";
ALTER INDEX "uk_user_bind_user_platform" RENAME TO "uk_user_third_party_account_user_platform";
COMMENT ON TABLE "user_third_party_account" IS '用户第三方平台账号绑定表';
