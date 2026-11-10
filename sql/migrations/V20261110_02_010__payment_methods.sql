-- 目的：初始化公共支付方式，仅供 PostgreSQL 18+ 新库使用。
-- 前置：已执行 V20261110_01 结构迁移及之前的适用版本。
-- 影响：插入公共预设，不创建用户账号；源库搬迁仍由独立导入器处理。
-- 重跑：由 Flyway 版本历史保证只执行一次；失败事务回滚后修正原因再迁移。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate。
-- BEGIN ORIGINAL SEED SQL
INSERT INTO "user_dict_data" ("id", "user_id", "template_id", "dict_type", "sort_order", "dict_label", "dict_value", "color", "icon", "ext_data", "is_default", "is_readonly", "create_user", "create_time", "update_user", "update_time", "remark", "is_deleted", "is_enabled") VALUES
(1, 0, NULL, 'pay_type', 1, '支付宝', '1', NULL, NULL, NULL, 'N', 'N', 0, '2026-11-10 11:39:36', 0, '2026-11-10 11:39:36', NULL, 0, 1),
(2, 0, NULL, 'pay_type', 2, '微信', '2', NULL, NULL, NULL, 'N', 'N', 0, '2026-11-10 11:39:36', 0, '2026-11-10 11:39:36', NULL, 0, 1),
(3, 0, NULL, 'pay_type', 3, '现金', '3', NULL, NULL, NULL, 'N', 'N', 0, '2026-11-10 11:39:36', 0, '2026-11-10 11:39:36', NULL, 0, 1),
(4, 0, NULL, 'pay_type', 4, '银行卡', '4', NULL, NULL, NULL, 'N', 'N', 0, '2026-11-10 11:39:36', 0, '2026-11-10 11:39:36', NULL, 0, 1),
(5, 0, NULL, 'pay_type', 5, '广发信用卡', '5', NULL, NULL, NULL, 'N', 'N', 0, '2026-11-10 11:39:36', 0, '2026-11-10 11:39:36', NULL, 0, 1),
(6, 0, NULL, 'pay_type', 6, '招商信号卡', '6', NULL, NULL, NULL, 'N', 'N', 0, '2026-11-10 11:39:36', 0, '2026-11-10 11:39:36', NULL, 0, 1),
(7, 0, NULL, 'pay_type', 7, '京东', '7', NULL, NULL, NULL, 'N', 'N', 0, '2026-11-10 11:39:36', 0, '2026-11-10 11:39:36', NULL, 0, 1);
