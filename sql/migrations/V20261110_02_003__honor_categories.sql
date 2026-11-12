-- 目的：初始化荣誉分类，仅供 PostgreSQL 18+ 新库使用。
-- 前置：已执行 V20261110_01 结构迁移及之前的适用版本。
-- 影响：插入公共预设，不创建用户账号；源库搬迁仍由独立导入器处理。
-- 重跑：由 Flyway 版本历史保证只执行一次；失败事务回滚后修正原因再迁移。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate。
INSERT INTO honor_category ("id", "user_id", "name", "icon", "color", "sort_order", "is_deleted", "create_user", "update_user") VALUES
(1, NULL, '学业成就', '🎓', '#4CAF50', 1, 0, NULL, NULL),
(2, NULL, '奖学金', '💰', '#FFC107', 2, 0, NULL, NULL),
(3, NULL, '工作荣誉', '💼', '#2196F3', 3, 0, NULL, NULL),
(4, NULL, '竞赛获奖', '🏆', '#FF5722', 4, 0, NULL, NULL),
(5, NULL, '社会实践', '🤝', '#9C27B0', 5, 0, NULL, NULL),
(6, NULL, '荣誉称号', '⭐', '#FFD700', 6, 0, NULL, NULL),
(7, NULL, '其他荣誉', '📜', '#607D8B', 7, 0, NULL, NULL);
