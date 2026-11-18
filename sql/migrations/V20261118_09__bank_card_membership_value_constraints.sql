-- 目的：为银行卡和会员记录补齐与枚举无关的取值约束，阻止脚本或旁路写入保存越界日数、负数金额和倒置日期。
-- 前置：PostgreSQL 18+；已执行 V20261118_08；执行前备份并暂停银行卡、会员写入。
-- 影响：只新增 CHECK 约束，不修改列、索引和业务行；存在越界值时整次迁移失败并列出对应表和列。
-- 重跑：Flyway 成功后不重跑；预检失败时先人工核对异常行，修正后对同一版本重新执行。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate。
-- 验证：账单日/还款日 1-31、金额非负、有效期存当月第一天、会员到期日不早于开通日；NULL 仍按未记录处理。

SET LOCAL lock_timeout = '10s';

-- 1. 存量预检：约束对逻辑删除行同样生效，因此不按 is_deleted 过滤。
DO $$
DECLARE
    invalid_rows bigint;
BEGIN
    SELECT count(*) INTO invalid_rows FROM bank_card
     WHERE statement_day IS NOT NULL AND statement_day NOT BETWEEN 1 AND 31;
    IF invalid_rows > 0 THEN
        RAISE EXCEPTION 'bank_card.statement_day 有 % 行不在 1-31 范围，请先人工核对', invalid_rows;
    END IF;

    SELECT count(*) INTO invalid_rows FROM bank_card
     WHERE repayment_day IS NOT NULL AND repayment_day NOT BETWEEN 1 AND 31;
    IF invalid_rows > 0 THEN
        RAISE EXCEPTION 'bank_card.repayment_day 有 % 行不在 1-31 范围，请先人工核对', invalid_rows;
    END IF;

    SELECT count(*) INTO invalid_rows FROM bank_card WHERE balance < 0;
    IF invalid_rows > 0 THEN
        RAISE EXCEPTION 'bank_card.balance 有 % 行为负数，请先人工核对', invalid_rows;
    END IF;

    SELECT count(*) INTO invalid_rows FROM bank_card WHERE credit_limit < 0;
    IF invalid_rows > 0 THEN
        RAISE EXCEPTION 'bank_card.credit_limit 有 % 行为负数，请先人工核对', invalid_rows;
    END IF;

    SELECT count(*) INTO invalid_rows FROM bank_card
     WHERE expiry_month IS NOT NULL AND EXTRACT(DAY FROM expiry_month) <> 1;
    IF invalid_rows > 0 THEN
        RAISE EXCEPTION 'bank_card.expiry_month 有 % 行不是当月第一天，请先人工核对', invalid_rows;
    END IF;

    SELECT count(*) INTO invalid_rows FROM membership_record
     WHERE start_date IS NOT NULL AND expiry_date < start_date;
    IF invalid_rows > 0 THEN
        RAISE EXCEPTION 'membership_record 有 % 行到期日早于开通日，请先人工核对', invalid_rows;
    END IF;

    SELECT count(*) INTO invalid_rows FROM membership_record WHERE price < 0;
    IF invalid_rows > 0 THEN
        RAISE EXCEPTION 'membership_record.price 有 % 行为负数，请先人工核对', invalid_rows;
    END IF;

    SELECT count(*) INTO invalid_rows FROM membership_record WHERE monthly_amount < 0;
    IF invalid_rows > 0 THEN
        RAISE EXCEPTION 'membership_record.monthly_amount 有 % 行为负数，请先人工核对', invalid_rows;
    END IF;
END $$;

-- 2. 银行卡：日数范围、金额非负、有效期只存月份第一天；为空表示未记录。
ALTER TABLE bank_card
  ADD CONSTRAINT ck_bank_card_statement_day
    CHECK (statement_day IS NULL OR statement_day BETWEEN 1 AND 31),
  ADD CONSTRAINT ck_bank_card_repayment_day
    CHECK (repayment_day IS NULL OR repayment_day BETWEEN 1 AND 31),
  ADD CONSTRAINT ck_bank_card_balance
    CHECK (balance IS NULL OR balance >= 0),
  ADD CONSTRAINT ck_bank_card_credit_limit
    CHECK (credit_limit IS NULL OR credit_limit >= 0),
  ADD CONSTRAINT ck_bank_card_expiry_month
    CHECK (expiry_month IS NULL OR EXTRACT(DAY FROM expiry_month) = 1);

COMMENT ON CONSTRAINT ck_bank_card_statement_day ON bank_card IS '账单日为空或 1-31；与请求校验一致';
COMMENT ON CONSTRAINT ck_bank_card_repayment_day ON bank_card IS '固定还款日为空或 1-31；与请求校验一致';
COMMENT ON CONSTRAINT ck_bank_card_balance ON bank_card IS '储蓄卡余额为空或非负数';
COMMENT ON CONSTRAINT ck_bank_card_credit_limit ON bank_card IS '信用额度为空或非负数，0 表示明确零额度';
COMMENT ON CONSTRAINT ck_bank_card_expiry_month ON bank_card IS '有效期为空或只存当月第一天';

-- 3. 会员记录：到期日不早于开通日，金额非负；枚举仍由请求和服务层维护。
ALTER TABLE membership_record
  ADD CONSTRAINT ck_membership_record_period
    CHECK (start_date IS NULL OR expiry_date >= start_date),
  ADD CONSTRAINT ck_membership_record_price
    CHECK (price IS NULL OR price >= 0),
  ADD CONSTRAINT ck_membership_record_monthly_amount
    CHECK (monthly_amount >= 0);

COMMENT ON CONSTRAINT ck_membership_record_period ON membership_record IS '开通日为空或到期日不早于开通日';
COMMENT ON CONSTRAINT ck_membership_record_price ON membership_record IS '支付金额为空或非负数';
COMMENT ON CONSTRAINT ck_membership_record_monthly_amount ON membership_record IS '折算月均成本非负数';