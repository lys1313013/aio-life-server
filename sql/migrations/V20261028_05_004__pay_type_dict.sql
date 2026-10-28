-- 前置：已通过 Flyway 完成结构基线及版本 20261028.04 之前的结构迁移。

-- 支付方式（pay_type）公共字典种子数据
-- 提取自 1_init_table/2026-06-07_migrate_pay_type.sql（2026-08-18 合并重建时迁移）
-- 共 7 条公共字典（user_id=0 表示公共）

-- 只补缺：按公共用户、字典类型及键值/标签匹配，包含软删除项；无唯一索引也不会重复插入。
INSERT INTO `user_dict_data` (`user_id`, `dict_type`, `sort_order`, `dict_label`, `dict_value`, `is_enabled`, `create_user`, `update_user`)
SELECT 0, 'pay_type', 1, '支付宝', '1', 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `user_dict_data` WHERE `user_id` = 0 AND `dict_type` = 'pay_type' AND (`dict_value` = '1' OR `dict_label` = '支付宝'));
INSERT INTO `user_dict_data` (`user_id`, `dict_type`, `sort_order`, `dict_label`, `dict_value`, `is_enabled`, `create_user`, `update_user`)
SELECT 0, 'pay_type', 2, '微信', '2', 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `user_dict_data` WHERE `user_id` = 0 AND `dict_type` = 'pay_type' AND (`dict_value` = '2' OR `dict_label` = '微信'));
INSERT INTO `user_dict_data` (`user_id`, `dict_type`, `sort_order`, `dict_label`, `dict_value`, `is_enabled`, `create_user`, `update_user`)
SELECT 0, 'pay_type', 3, '现金', '3', 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `user_dict_data` WHERE `user_id` = 0 AND `dict_type` = 'pay_type' AND (`dict_value` = '3' OR `dict_label` = '现金'));
INSERT INTO `user_dict_data` (`user_id`, `dict_type`, `sort_order`, `dict_label`, `dict_value`, `is_enabled`, `create_user`, `update_user`)
SELECT 0, 'pay_type', 4, '银行卡', '4', 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `user_dict_data` WHERE `user_id` = 0 AND `dict_type` = 'pay_type' AND (`dict_value` = '4' OR `dict_label` = '银行卡'));
INSERT INTO `user_dict_data` (`user_id`, `dict_type`, `sort_order`, `dict_label`, `dict_value`, `is_enabled`, `create_user`, `update_user`)
SELECT 0, 'pay_type', 5, '广发信用卡', '5', 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `user_dict_data` WHERE `user_id` = 0 AND `dict_type` = 'pay_type' AND (`dict_value` = '5' OR `dict_label` = '广发信用卡'));
INSERT INTO `user_dict_data` (`user_id`, `dict_type`, `sort_order`, `dict_label`, `dict_value`, `is_enabled`, `create_user`, `update_user`)
SELECT 0, 'pay_type', 6, '招商信号卡', '6', 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `user_dict_data` WHERE `user_id` = 0 AND `dict_type` = 'pay_type' AND (`dict_value` = '6' OR `dict_label` = '招商信号卡'));
INSERT INTO `user_dict_data` (`user_id`, `dict_type`, `sort_order`, `dict_label`, `dict_value`, `is_enabled`, `create_user`, `update_user`)
SELECT 0, 'pay_type', 7, '京东', '7', 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `user_dict_data` WHERE `user_id` = 0 AND `dict_type` = 'pay_type' AND (`dict_value` = '7' OR `dict_label` = '京东'));
