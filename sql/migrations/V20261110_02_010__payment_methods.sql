-- 目的：初始化支付方式。
-- 前置：PostgreSQL 18+，基线表结构已创建。
-- 影响：写入公共种子。来源为 V20261110_02_010 的公共支付方式。
-- 重跑：由 Flyway 基线事务管理，失败整体回滚。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate

INSERT INTO "user_dict_data" ("id", "user_id", "template_id", "dict_type", "sort_order", "dict_label", "dict_value", "color", "icon", "extra_data", "is_default", "is_readonly", "create_user", "create_time", "update_user", "update_time", "remark", "is_deleted", "is_enabled") VALUES
	(1, NULL, NULL, 'pay_type', 1, '支付宝', '1', NULL, NULL, NULL, 0, 0, NULL, '2026-11-10 11:39:36', NULL, '2026-11-10 11:39:36', NULL, 0, 1),
	(2, NULL, NULL, 'pay_type', 2, '微信', '2', NULL, NULL, NULL, 0, 0, NULL, '2026-11-10 11:39:36', NULL, '2026-11-10 11:39:36', NULL, 0, 1),
	(3, NULL, NULL, 'pay_type', 3, '现金', '3', NULL, NULL, NULL, 0, 0, NULL, '2026-11-10 11:39:36', NULL, '2026-11-10 11:39:36', NULL, 0, 1),
	(4, NULL, NULL, 'pay_type', 4, '银行卡', '4', NULL, NULL, NULL, 0, 0, NULL, '2026-11-10 11:39:36', NULL, '2026-11-10 11:39:36', NULL, 0, 1),
	(5, NULL, NULL, 'pay_type', 5, '广发信用卡', '5', NULL, NULL, NULL, 0, 0, NULL, '2026-11-10 11:39:36', NULL, '2026-11-10 11:39:36', NULL, 0, 1),
	(6, NULL, NULL, 'pay_type', 6, '招商信用卡', '6', NULL, NULL, NULL, 0, 0, NULL, '2026-11-10 11:39:36', NULL, '2026-11-10 11:39:36', NULL, 0, 1),
	(7, NULL, NULL, 'pay_type', 7, '京东', '7', NULL, NULL, NULL, 0, 0, NULL, '2026-11-10 11:39:36', NULL, '2026-11-10 11:39:36', NULL, 0, 1);
