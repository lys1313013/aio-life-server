-- 目的：财务表的类型与时间、描述列使用完整业务前缀，避免 exp_/inc_ 缩写在不同表中产生歧义；接口字段同步为 expenseTypeId、incomeTypeId、expenseTime、expenseDesc。
-- 前置：PostgreSQL 18+；已执行 V20261118_02；四列类型与可空性保持不变。
-- 影响：只重命名列并更新注释，不改类型、默认值、可空性、数据、索引或约束；expense_idx_user_deleted_time 随列自动指向新列，名称按现有用途保留。
-- 重跑：Flyway 成功后不重跑；重复执行会因旧列名不存在而失败。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；失败事务回滚。

ALTER TABLE "expense" RENAME COLUMN "exp_type_id" TO "expense_type_id";
ALTER TABLE "expense" RENAME COLUMN "exp_time" TO "expense_time";
ALTER TABLE "expense" RENAME COLUMN "exp_desc" TO "expense_desc";
ALTER TABLE "income" RENAME COLUMN "inc_type_id" TO "income_type_id";

COMMENT ON COLUMN "expense"."expense_type_id" IS '支出类型ID';
COMMENT ON COLUMN "expense"."expense_time" IS '支出时间';
COMMENT ON COLUMN "expense"."expense_desc" IS '交易描述，未记录时为空';
COMMENT ON COLUMN "income"."income_type_id" IS '收入类型ID';