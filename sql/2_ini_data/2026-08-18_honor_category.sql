-- 依赖 1_init_table 已执行
USE `aio-life`;
-- 荣誉分类系统预设种子数据
-- 提取自 1_init_table/2026-04-11_create_honor_table.sql（2026-08-18 合并重建时迁移）
-- 共 7 条系统预设分类

INSERT INTO `honor_category` (`id`, `user_id`, `name`, `icon`, `color`, `sort_order`) VALUES
(1, NULL, '学业成就', '🎓', '#4CAF50', 1),
(2, NULL, '奖学金', '💰', '#FFC107', 2),
(3, NULL, '工作荣誉', '💼', '#2196F3', 3),
(4, NULL, '竞赛获奖', '🏆', '#FF5722', 4),
(5, NULL, '社会实践', '🤝', '#9C27B0', 5),
(6, NULL, '荣誉称号', '⭐', '#FFD700', 6),
(7, NULL, '其他荣誉', '📜', '#607D8B', 7);
