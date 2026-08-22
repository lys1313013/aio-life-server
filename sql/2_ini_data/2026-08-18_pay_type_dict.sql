-- 依赖 1_init_table 已执行
USE `aio_life`;
-- 支付方式（pay_type）公共字典种子数据
-- 提取自 1_init_table/2026-06-07_migrate_pay_type.sql（2026-08-18 合并重建时迁移）
-- 共 7 条公共字典（user_id=0 表示公共）

INSERT IGNORE INTO `user_dict_data` (`user_id`, `dict_type`, `dict_sort`, `dict_label`, `dict_value`, `status`, `create_user`, `update_user`)
VALUES 
(0, 'pay_type', 1, '支付宝', '1', '0', 0, 0),
(0, 'pay_type', 2, '微信', '2', '0', 0, 0),
(0, 'pay_type', 3, '现金', '3', '0', 0, 0),
(0, 'pay_type', 4, '银行卡', '4', '0', 0, 0),
(0, 'pay_type', 5, '广发信用卡', '5', '0', 0, 0),
(0, 'pay_type', 6, '招商信号卡', '6', '0', 0, 0),
(0, 'pay_type', 7, '京东', '7', '0', 0, 0);

