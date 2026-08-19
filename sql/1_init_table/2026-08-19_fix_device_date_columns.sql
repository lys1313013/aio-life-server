-- 修正设备日期字段类型
-- 空字符串无法转换为 DATE，迁移前统一清理为 NULL；其他非法日期应人工修正。
UPDATE `device`
SET `purchase_date` = NULL
WHERE `purchase_date` IS NOT NULL
  AND TRIM(`purchase_date`) = '';

UPDATE `device`
SET `end_date` = NULL
WHERE `end_date` IS NOT NULL
  AND TRIM(`end_date`) = '';

ALTER TABLE `device`
    MODIFY COLUMN `purchase_date` DATE DEFAULT NULL COMMENT '购买日期',
    MODIFY COLUMN `end_date` DATE DEFAULT NULL COMMENT '结束日期（用于计算日均费用）';
