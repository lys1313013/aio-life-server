-- 目的：初始化数据导出协调记录，仅供 PostgreSQL 18+ 新库使用。
-- 前置：已执行 V20261110_01 结构迁移及之前的适用版本。
-- 影响：插入公共预设，不创建用户账号；源库搬迁仍由独立导入器处理。
-- 重跑：由 Flyway 版本历史保证只执行一次；失败事务回滚后修正原因再迁移。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate。
INSERT INTO data_export_coordinator ("id", "name", "create_user", "create_time", "update_user", "update_time", "is_deleted") VALUES
(1, 'global', NULL, '2026-11-10 11:39:35.930000', NULL, '2026-11-10 11:39:35.930000', 0);
