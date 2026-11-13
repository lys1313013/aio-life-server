-- 目的：初始化系统配置，仅供 PostgreSQL 18+ 新库使用。
-- 前置：已执行 V20261110_01 结构迁移及之前的适用版本。
-- 影响：插入公共预设，不创建用户账号；源库搬迁仍由独立导入器处理。
-- 重跑：由 Flyway 版本历史保证只执行一次；失败事务回滚后修正原因再迁移。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate。
INSERT INTO sys_config ("id", "config_key", "config_value", "config_type", "description", "is_deleted", "create_time", "update_time", "create_user", "update_user") VALUES
(1, 'feedback.notify_admin_ids', '[]', 'JSON', '接收用户反馈通知的管理员账号 ID 列表', 0, '2026-11-10 11:39:36', '2026-11-10 11:39:36', 1, 1),
(2026102701, 'privacy.operator', '', 'STRING', '隐私政策公开的实际运营主体名称，发布前填写真实个人或组织名称', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, NULL),
(2026102702, 'privacy.contact-email', '', 'STRING', '隐私政策公开的联系邮箱，发布前填写可收信地址', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, NULL);
