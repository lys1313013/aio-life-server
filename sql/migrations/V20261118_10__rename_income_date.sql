-- 目的：收入表的日期列使用完整业务前缀，避免 inc_date 与其他表中的日期列产生歧义；接口字段同步为 incomeDate。
-- 前置：PostgreSQL 18+；已执行 V20261118_09；income.inc_date 仍为 date。
-- 影响：只重命名列并更新注释，不改类型、默认值、可空性、数据、索引或约束；索引 income_idx_user_deleted_date 随列自动指向新列，名称按现有用途保留。
-- 重跑：Flyway 成功后不重跑；重复执行会因旧列名不存在而失败。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；失败事务回滚。

ALTER TABLE "income" RENAME COLUMN "inc_date" TO "income_date";

COMMENT ON COLUMN "income"."income_date" IS '收入时间';