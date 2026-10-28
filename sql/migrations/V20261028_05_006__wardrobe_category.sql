-- 前置：已通过 Flyway 完成结构基线及版本 20261028.04 之前的结构迁移。

-- 衣柜分类系统预设种子数据
-- 提取自 1_init_table/wardrobe.sql（2026-08-18 合并重建时迁移）
-- 共 20 条预设分类（5 条一级分类 + 15 条二级分类）

-- 只补缺：按主键或同父级同名系统分类匹配，含软删除项，保留已有名称、图标、排序和状态。
-- 子分类使用已有系统父级 ID；父级已软删除时不补子分类，同名父级歧义会报错停止。
INSERT INTO `wardrobe_category` (`id`, `name`, `icon`, `parent_id`, `sort_order`, `category_type`)
SELECT 1, '上衣', 'shirt', NULL, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = 1 OR (`category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL AND `name` = '上衣'));
INSERT INTO `wardrobe_category` (`id`, `name`, `icon`, `parent_id`, `sort_order`, `category_type`)
SELECT 2, '下装', 'pants', NULL, 2, 0
WHERE NOT EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = 2 OR (`category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL AND `name` = '下装'));
INSERT INTO `wardrobe_category` (`id`, `name`, `icon`, `parent_id`, `sort_order`, `category_type`)
SELECT 3, '鞋子', 'shoe', NULL, 3, 0
WHERE NOT EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = 3 OR (`category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL AND `name` = '鞋子'));
INSERT INTO `wardrobe_category` (`id`, `name`, `icon`, `parent_id`, `sort_order`, `category_type`)
SELECT 4, '配饰', 'accessory', NULL, 4, 0
WHERE NOT EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = 4 OR (`category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL AND `name` = '配饰'));
INSERT INTO `wardrobe_category` (`id`, `name`, `icon`, `parent_id`, `sort_order`, `category_type`)
SELECT 5, '外套', 'jacket', NULL, 5, 0
WHERE NOT EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = 5 OR (`category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL AND `name` = '外套'));
SET @aio_wardrobe_parent = COALESCE(
  (SELECT `id` FROM `wardrobe_category` WHERE `id` = 1 AND `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL),
  (SELECT `id` FROM `wardrobe_category` WHERE `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL AND `name` = '上衣')
);
INSERT INTO `wardrobe_category` (`id`, `name`, `icon`, `parent_id`, `sort_order`, `category_type`)
SELECT 101, 'T恤', 'shirt', @aio_wardrobe_parent, 1, 0
WHERE EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = @aio_wardrobe_parent AND `is_deleted` = 0)
  AND NOT EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = 101 OR (`category_type` = 0 AND `user_id` IS NULL AND `parent_id` = @aio_wardrobe_parent AND `name` = 'T恤'));
SET @aio_wardrobe_parent = COALESCE(
  (SELECT `id` FROM `wardrobe_category` WHERE `id` = 1 AND `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL),
  (SELECT `id` FROM `wardrobe_category` WHERE `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL AND `name` = '上衣')
);
INSERT INTO `wardrobe_category` (`id`, `name`, `icon`, `parent_id`, `sort_order`, `category_type`)
SELECT 102, '衬衫', 'shirt', @aio_wardrobe_parent, 2, 0
WHERE EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = @aio_wardrobe_parent AND `is_deleted` = 0)
  AND NOT EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = 102 OR (`category_type` = 0 AND `user_id` IS NULL AND `parent_id` = @aio_wardrobe_parent AND `name` = '衬衫'));
SET @aio_wardrobe_parent = COALESCE(
  (SELECT `id` FROM `wardrobe_category` WHERE `id` = 1 AND `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL),
  (SELECT `id` FROM `wardrobe_category` WHERE `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL AND `name` = '上衣')
);
INSERT INTO `wardrobe_category` (`id`, `name`, `icon`, `parent_id`, `sort_order`, `category_type`)
SELECT 103, '卫衣', 'shirt', @aio_wardrobe_parent, 3, 0
WHERE EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = @aio_wardrobe_parent AND `is_deleted` = 0)
  AND NOT EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = 103 OR (`category_type` = 0 AND `user_id` IS NULL AND `parent_id` = @aio_wardrobe_parent AND `name` = '卫衣'));
SET @aio_wardrobe_parent = COALESCE(
  (SELECT `id` FROM `wardrobe_category` WHERE `id` = 2 AND `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL),
  (SELECT `id` FROM `wardrobe_category` WHERE `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL AND `name` = '下装')
);
INSERT INTO `wardrobe_category` (`id`, `name`, `icon`, `parent_id`, `sort_order`, `category_type`)
SELECT 201, '牛仔裤', 'pants', @aio_wardrobe_parent, 1, 0
WHERE EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = @aio_wardrobe_parent AND `is_deleted` = 0)
  AND NOT EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = 201 OR (`category_type` = 0 AND `user_id` IS NULL AND `parent_id` = @aio_wardrobe_parent AND `name` = '牛仔裤'));
SET @aio_wardrobe_parent = COALESCE(
  (SELECT `id` FROM `wardrobe_category` WHERE `id` = 2 AND `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL),
  (SELECT `id` FROM `wardrobe_category` WHERE `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL AND `name` = '下装')
);
INSERT INTO `wardrobe_category` (`id`, `name`, `icon`, `parent_id`, `sort_order`, `category_type`)
SELECT 202, '休闲裤', 'pants', @aio_wardrobe_parent, 2, 0
WHERE EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = @aio_wardrobe_parent AND `is_deleted` = 0)
  AND NOT EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = 202 OR (`category_type` = 0 AND `user_id` IS NULL AND `parent_id` = @aio_wardrobe_parent AND `name` = '休闲裤'));
SET @aio_wardrobe_parent = COALESCE(
  (SELECT `id` FROM `wardrobe_category` WHERE `id` = 2 AND `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL),
  (SELECT `id` FROM `wardrobe_category` WHERE `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL AND `name` = '下装')
);
INSERT INTO `wardrobe_category` (`id`, `name`, `icon`, `parent_id`, `sort_order`, `category_type`)
SELECT 203, '裙子', 'skirt', @aio_wardrobe_parent, 3, 0
WHERE EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = @aio_wardrobe_parent AND `is_deleted` = 0)
  AND NOT EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = 203 OR (`category_type` = 0 AND `user_id` IS NULL AND `parent_id` = @aio_wardrobe_parent AND `name` = '裙子'));
SET @aio_wardrobe_parent = COALESCE(
  (SELECT `id` FROM `wardrobe_category` WHERE `id` = 3 AND `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL),
  (SELECT `id` FROM `wardrobe_category` WHERE `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL AND `name` = '鞋子')
);
INSERT INTO `wardrobe_category` (`id`, `name`, `icon`, `parent_id`, `sort_order`, `category_type`)
SELECT 301, '运动鞋', 'shoe', @aio_wardrobe_parent, 1, 0
WHERE EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = @aio_wardrobe_parent AND `is_deleted` = 0)
  AND NOT EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = 301 OR (`category_type` = 0 AND `user_id` IS NULL AND `parent_id` = @aio_wardrobe_parent AND `name` = '运动鞋'));
SET @aio_wardrobe_parent = COALESCE(
  (SELECT `id` FROM `wardrobe_category` WHERE `id` = 3 AND `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL),
  (SELECT `id` FROM `wardrobe_category` WHERE `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL AND `name` = '鞋子')
);
INSERT INTO `wardrobe_category` (`id`, `name`, `icon`, `parent_id`, `sort_order`, `category_type`)
SELECT 302, '皮鞋', 'shoe', @aio_wardrobe_parent, 2, 0
WHERE EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = @aio_wardrobe_parent AND `is_deleted` = 0)
  AND NOT EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = 302 OR (`category_type` = 0 AND `user_id` IS NULL AND `parent_id` = @aio_wardrobe_parent AND `name` = '皮鞋'));
SET @aio_wardrobe_parent = COALESCE(
  (SELECT `id` FROM `wardrobe_category` WHERE `id` = 3 AND `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL),
  (SELECT `id` FROM `wardrobe_category` WHERE `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL AND `name` = '鞋子')
);
INSERT INTO `wardrobe_category` (`id`, `name`, `icon`, `parent_id`, `sort_order`, `category_type`)
SELECT 303, '拖鞋', 'shoe', @aio_wardrobe_parent, 3, 0
WHERE EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = @aio_wardrobe_parent AND `is_deleted` = 0)
  AND NOT EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = 303 OR (`category_type` = 0 AND `user_id` IS NULL AND `parent_id` = @aio_wardrobe_parent AND `name` = '拖鞋'));
SET @aio_wardrobe_parent = COALESCE(
  (SELECT `id` FROM `wardrobe_category` WHERE `id` = 4 AND `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL),
  (SELECT `id` FROM `wardrobe_category` WHERE `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL AND `name` = '配饰')
);
INSERT INTO `wardrobe_category` (`id`, `name`, `icon`, `parent_id`, `sort_order`, `category_type`)
SELECT 401, '帽子', 'hat', @aio_wardrobe_parent, 1, 0
WHERE EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = @aio_wardrobe_parent AND `is_deleted` = 0)
  AND NOT EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = 401 OR (`category_type` = 0 AND `user_id` IS NULL AND `parent_id` = @aio_wardrobe_parent AND `name` = '帽子'));
SET @aio_wardrobe_parent = COALESCE(
  (SELECT `id` FROM `wardrobe_category` WHERE `id` = 4 AND `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL),
  (SELECT `id` FROM `wardrobe_category` WHERE `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL AND `name` = '配饰')
);
INSERT INTO `wardrobe_category` (`id`, `name`, `icon`, `parent_id`, `sort_order`, `category_type`)
SELECT 402, '围巾', 'scarf', @aio_wardrobe_parent, 2, 0
WHERE EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = @aio_wardrobe_parent AND `is_deleted` = 0)
  AND NOT EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = 402 OR (`category_type` = 0 AND `user_id` IS NULL AND `parent_id` = @aio_wardrobe_parent AND `name` = '围巾'));
SET @aio_wardrobe_parent = COALESCE(
  (SELECT `id` FROM `wardrobe_category` WHERE `id` = 4 AND `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL),
  (SELECT `id` FROM `wardrobe_category` WHERE `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL AND `name` = '配饰')
);
INSERT INTO `wardrobe_category` (`id`, `name`, `icon`, `parent_id`, `sort_order`, `category_type`)
SELECT 403, '手表', 'watch', @aio_wardrobe_parent, 3, 0
WHERE EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = @aio_wardrobe_parent AND `is_deleted` = 0)
  AND NOT EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = 403 OR (`category_type` = 0 AND `user_id` IS NULL AND `parent_id` = @aio_wardrobe_parent AND `name` = '手表'));
SET @aio_wardrobe_parent = COALESCE(
  (SELECT `id` FROM `wardrobe_category` WHERE `id` = 5 AND `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL),
  (SELECT `id` FROM `wardrobe_category` WHERE `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL AND `name` = '外套')
);
INSERT INTO `wardrobe_category` (`id`, `name`, `icon`, `parent_id`, `sort_order`, `category_type`)
SELECT 501, '夹克', 'jacket', @aio_wardrobe_parent, 1, 0
WHERE EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = @aio_wardrobe_parent AND `is_deleted` = 0)
  AND NOT EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = 501 OR (`category_type` = 0 AND `user_id` IS NULL AND `parent_id` = @aio_wardrobe_parent AND `name` = '夹克'));
SET @aio_wardrobe_parent = COALESCE(
  (SELECT `id` FROM `wardrobe_category` WHERE `id` = 5 AND `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL),
  (SELECT `id` FROM `wardrobe_category` WHERE `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL AND `name` = '外套')
);
INSERT INTO `wardrobe_category` (`id`, `name`, `icon`, `parent_id`, `sort_order`, `category_type`)
SELECT 502, '大衣', 'coat', @aio_wardrobe_parent, 2, 0
WHERE EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = @aio_wardrobe_parent AND `is_deleted` = 0)
  AND NOT EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = 502 OR (`category_type` = 0 AND `user_id` IS NULL AND `parent_id` = @aio_wardrobe_parent AND `name` = '大衣'));
SET @aio_wardrobe_parent = COALESCE(
  (SELECT `id` FROM `wardrobe_category` WHERE `id` = 5 AND `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL),
  (SELECT `id` FROM `wardrobe_category` WHERE `category_type` = 0 AND `user_id` IS NULL AND `parent_id` IS NULL AND `name` = '外套')
);
INSERT INTO `wardrobe_category` (`id`, `name`, `icon`, `parent_id`, `sort_order`, `category_type`)
SELECT 503, '羽绒服', 'down', @aio_wardrobe_parent, 3, 0
WHERE EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = @aio_wardrobe_parent AND `is_deleted` = 0)
  AND NOT EXISTS (SELECT 1 FROM `wardrobe_category` WHERE `id` = 503 OR (`category_type` = 0 AND `user_id` IS NULL AND `parent_id` = @aio_wardrobe_parent AND `name` = '羽绒服'));
