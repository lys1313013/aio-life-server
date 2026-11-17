-- 目的：把收支金额列改为表达业务口径的名称，避免“金额”被误解为同一个值。
-- 前置：PostgreSQL 18+；已执行 V20261117_03。
-- 影响：只重命名列并更新注释，不改类型、默认值、可空性、数据、索引或约束。
-- 重跑：Flyway 只执行一次；全部 DDL 在同一事务执行，失败整体回滚，修正后对同一版本重新执行。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；遇错停止。

ALTER TABLE "expense" RENAME COLUMN "amt" TO "bookkeeping_amount";
ALTER TABLE "expense" RENAME COLUMN "transaction_amt" TO "transaction_amount";
ALTER TABLE "income" RENAME COLUMN "amt" TO "gross_amount";
ALTER TABLE "income" RENAME COLUMN "tax" TO "tax_amount";

COMMENT ON COLUMN "expense"."bookkeeping_amount" IS '记账金额，计入支出统计；退款或手工调整后可与原始交易金额不同';
COMMENT ON COLUMN "expense"."transaction_amount" IS '支付平台原始交易金额；NULL表示未记录，不能假定等于记账金额';
COMMENT ON COLUMN "income"."gross_amount" IS '收入金额，未减税额；计入收入统计';
COMMENT ON COLUMN "income"."tax_amount" IS '收入税额，单位元；NULL表示未记录，0表示零税额';
