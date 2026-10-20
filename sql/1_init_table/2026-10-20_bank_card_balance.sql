-- 已有库只执行一次；历史卡片余额保留 NULL，不改写信用额度。
USE `aio_life`;

ALTER TABLE `bank_card`
    ADD COLUMN `balance` DECIMAL(18,2) DEFAULT NULL COMMENT '储蓄卡余额，人民币元；NULL未记录' AFTER `expiry_month`;
