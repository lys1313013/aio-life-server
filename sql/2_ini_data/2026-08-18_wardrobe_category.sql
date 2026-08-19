-- 依赖 1_init_table 已执行
USE `aio-life`;
-- 衣柜分类系统预设种子数据
-- 提取自 1_init_table/wardrobe.sql（2026-08-18 合并重建时迁移）
-- 共 20 条预设分类（5 条一级分类 + 15 条二级分类）

INSERT INTO `wardrobe_category` (`id`, `name`, `icon`, `parent_id`, `sort`, `category_type`) VALUES
(1, '上衣', 'shirt', NULL, 1, 0),
(2, '下装', 'pants', NULL, 2, 0),
(3, '鞋子', 'shoe', NULL, 3, 0),
(4, '配饰', 'accessory', NULL, 4, 0),
(5, '外套', 'jacket', NULL, 5, 0);
INSERT INTO `wardrobe_category` (`id`, `name`, `icon`, `parent_id`, `sort`, `category_type`) VALUES
(101, 'T恤', 'shirt', 1, 1, 0),
(102, '衬衫', 'shirt', 1, 2, 0),
(103, '卫衣', 'shirt', 1, 3, 0),
(201, '牛仔裤', 'pants', 2, 1, 0),
(202, '休闲裤', 'pants', 2, 2, 0),
(203, '裙子', 'skirt', 2, 3, 0),
(301, '运动鞋', 'shoe', 3, 1, 0),
(302, '皮鞋', 'shoe', 3, 2, 0),
(303, '拖鞋', 'shoe', 3, 3, 0),
(401, '帽子', 'hat', 4, 1, 0),
(402, '围巾', 'scarf', 4, 2, 0),
(403, '手表', 'watch', 4, 3, 0),
(501, '夹克', 'jacket', 5, 1, 0),
(502, '大衣', 'coat', 5, 2, 0),
(503, '羽绒服', 'down', 5, 3, 0);
