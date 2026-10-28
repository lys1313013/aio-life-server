-- 前置：已通过 Flyway 完成结构基线及版本 20261028.04 之前的结构迁移。

-- 荣誉分类系统预设种子数据
-- 提取自 1_init_table/2026-04-11_create_honor_table.sql（2026-08-18 合并重建时迁移）
-- 共 7 条系统预设分类

-- 只补缺：相同主键或同名系统预设已存在（含软删除）时保留，不覆盖名称、图标或排序。
INSERT INTO `honor_category` (`id`, `user_id`, `name`, `icon`, `color`, `sort_order`)
SELECT 1, NULL, '学业成就', '🎓', '#4CAF50', 1
WHERE NOT EXISTS (SELECT 1 FROM `honor_category` WHERE `id` = 1 OR (`user_id` IS NULL AND `name` = '学业成就'));
INSERT INTO `honor_category` (`id`, `user_id`, `name`, `icon`, `color`, `sort_order`)
SELECT 2, NULL, '奖学金', '💰', '#FFC107', 2
WHERE NOT EXISTS (SELECT 1 FROM `honor_category` WHERE `id` = 2 OR (`user_id` IS NULL AND `name` = '奖学金'));
INSERT INTO `honor_category` (`id`, `user_id`, `name`, `icon`, `color`, `sort_order`)
SELECT 3, NULL, '工作荣誉', '💼', '#2196F3', 3
WHERE NOT EXISTS (SELECT 1 FROM `honor_category` WHERE `id` = 3 OR (`user_id` IS NULL AND `name` = '工作荣誉'));
INSERT INTO `honor_category` (`id`, `user_id`, `name`, `icon`, `color`, `sort_order`)
SELECT 4, NULL, '竞赛获奖', '🏆', '#FF5722', 4
WHERE NOT EXISTS (SELECT 1 FROM `honor_category` WHERE `id` = 4 OR (`user_id` IS NULL AND `name` = '竞赛获奖'));
INSERT INTO `honor_category` (`id`, `user_id`, `name`, `icon`, `color`, `sort_order`)
SELECT 5, NULL, '社会实践', '🤝', '#9C27B0', 5
WHERE NOT EXISTS (SELECT 1 FROM `honor_category` WHERE `id` = 5 OR (`user_id` IS NULL AND `name` = '社会实践'));
INSERT INTO `honor_category` (`id`, `user_id`, `name`, `icon`, `color`, `sort_order`)
SELECT 6, NULL, '荣誉称号', '⭐', '#FFD700', 6
WHERE NOT EXISTS (SELECT 1 FROM `honor_category` WHERE `id` = 6 OR (`user_id` IS NULL AND `name` = '荣誉称号'));
INSERT INTO `honor_category` (`id`, `user_id`, `name`, `icon`, `color`, `sort_order`)
SELECT 7, NULL, '其他荣誉', '📜', '#607D8B', 7
WHERE NOT EXISTS (SELECT 1 FROM `honor_category` WHERE `id` = 7 OR (`user_id` IS NULL AND `name` = '其他荣誉'));
