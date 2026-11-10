-- 目的：初始化衣柜分类，仅供 PostgreSQL 18+ 新库使用。
-- 前置：已执行 V20261110_01 结构迁移及之前的适用版本。
-- 影响：插入公共预设，不创建用户账号；源库搬迁仍由独立导入器处理。
-- 重跑：由 Flyway 版本历史保证只执行一次；失败事务回滚后修正原因再迁移。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate。
-- BEGIN ORIGINAL SEED SQL
INSERT INTO "wardrobe_category" ("id", "name", "icon", "parent_id", "sort_order", "category_type", "user_id", "create_user", "create_time", "update_user", "update_time", "is_deleted") VALUES
(1, '上衣', 'shirt', NULL, 1, 0, NULL, NULL, NULL, NULL, NULL, 0),
(2, '下装', 'pants', NULL, 2, 0, NULL, NULL, NULL, NULL, NULL, 0),
(3, '鞋子', 'shoe', NULL, 3, 0, NULL, NULL, NULL, NULL, NULL, 0),
(4, '配饰', 'accessory', NULL, 4, 0, NULL, NULL, NULL, NULL, NULL, 0),
(5, '外套', 'jacket', NULL, 5, 0, NULL, NULL, NULL, NULL, NULL, 0),
(101, 'T恤', 'shirt', 1, 1, 0, NULL, NULL, NULL, NULL, NULL, 0),
(102, '衬衫', 'shirt', 1, 2, 0, NULL, NULL, NULL, NULL, NULL, 0),
(103, '卫衣', 'shirt', 1, 3, 0, NULL, NULL, NULL, NULL, NULL, 0),
(201, '牛仔裤', 'pants', 2, 1, 0, NULL, NULL, NULL, NULL, NULL, 0),
(202, '休闲裤', 'pants', 2, 2, 0, NULL, NULL, NULL, NULL, NULL, 0),
(203, '裙子', 'skirt', 2, 3, 0, NULL, NULL, NULL, NULL, NULL, 0),
(301, '运动鞋', 'shoe', 3, 1, 0, NULL, NULL, NULL, NULL, NULL, 0),
(302, '皮鞋', 'shoe', 3, 2, 0, NULL, NULL, NULL, NULL, NULL, 0),
(303, '拖鞋', 'shoe', 3, 3, 0, NULL, NULL, NULL, NULL, NULL, 0),
(401, '帽子', 'hat', 4, 1, 0, NULL, NULL, NULL, NULL, NULL, 0),
(402, '围巾', 'scarf', 4, 2, 0, NULL, NULL, NULL, NULL, NULL, 0),
(403, '手表', 'watch', 4, 3, 0, NULL, NULL, NULL, NULL, NULL, 0),
(501, '夹克', 'jacket', 5, 1, 0, NULL, NULL, NULL, NULL, NULL, 0),
(502, '大衣', 'coat', 5, 2, 0, NULL, NULL, NULL, NULL, NULL, 0),
(503, '羽绒服', 'down', 5, 3, 0, NULL, NULL, NULL, NULL, NULL, 0);
