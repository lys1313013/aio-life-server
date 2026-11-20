-- 目的：初始化衣橱分类。
-- 前置：PostgreSQL 18+，基线表结构已创建。
-- 影响：写入公共种子。来源为 V20261110_02_011 的公共衣橱分类。
-- 重跑：由 Flyway 基线事务管理，失败整体回滚。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate

INSERT INTO "wardrobe_category" ("id", "name", "icon", "parent_id", "sort_order", "user_id", "create_user", "create_time", "update_user", "update_time", "is_deleted") VALUES
	(1, '上衣', 'shirt', NULL, 1, NULL, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0),
	(2, '下装', 'pants', NULL, 2, NULL, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0),
	(3, '鞋子', 'shoe', NULL, 3, NULL, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0),
	(4, '配饰', 'accessory', NULL, 4, NULL, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0),
	(5, '外套', 'jacket', NULL, 5, NULL, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0),
	(101, 'T恤', 'shirt', 1, 1, NULL, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0),
	(102, '衬衫', 'shirt', 1, 2, NULL, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0),
	(103, '卫衣', 'shirt', 1, 3, NULL, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0),
	(201, '牛仔裤', 'pants', 2, 1, NULL, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0),
	(202, '休闲裤', 'pants', 2, 2, NULL, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0),
	(203, '裙子', 'skirt', 2, 3, NULL, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0),
	(301, '运动鞋', 'shoe', 3, 1, NULL, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0),
	(302, '皮鞋', 'shoe', 3, 2, NULL, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0),
	(303, '拖鞋', 'shoe', 3, 3, NULL, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0),
	(401, '帽子', 'hat', 4, 1, NULL, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0),
	(402, '围巾', 'scarf', 4, 2, NULL, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0),
	(403, '手表', 'watch', 4, 3, NULL, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0),
	(501, '夹克', 'jacket', 5, 1, NULL, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0),
	(502, '大衣', 'coat', 5, 2, NULL, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0),
	(503, '羽绒服', 'down', 5, 3, NULL, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, 0);
