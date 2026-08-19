-- 会员计费周期与月均成本
ALTER TABLE `membership_record`
    ADD COLUMN `billing_cycle` VARCHAR(20) NOT NULL DEFAULT 'month'
        COMMENT '计费周期:week/two_weeks/month/quarter/half_year/year' AFTER `price`,
    ADD COLUMN `monthly_amount` DECIMAL(10,2) NULL
        COMMENT '折算后的月均成本，可手动修改' AFTER `billing_cycle`;

-- 历史数据按月付处理，月均成本等于原支付金额
UPDATE `membership_record`
SET `monthly_amount` = COALESCE(`price`, 0)
WHERE `monthly_amount` IS NULL;

ALTER TABLE `membership_record`
    MODIFY COLUMN `monthly_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00
        COMMENT '折算后的月均成本，可手动修改';
