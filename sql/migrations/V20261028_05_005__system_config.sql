-- 前置：已通过 Flyway 完成结构基线及版本 20261028.04 之前的结构迁移。

-- 系统配置种子数据
-- 提取自 1_init_table/2026-07-19_create_feedback.sql（2026-08-18 合并重建时迁移）
-- 共 1 条：反馈通知接收人配置（默认空列表，需管理员在后台勾选）。
-- 只补缺配置键，含软删除项，不覆盖已有接收人；固定 ID 被其他键占用时直接失败，需核实后处理。

INSERT INTO `system_config` (`id`, `config_key`, `config_value`, `config_type`, `description`, `is_deleted`, `create_time`, `update_time`, `create_user`, `update_user`)
SELECT 1, 'feedback.notify_admin_ids', '[]', 'JSON', '接收用户反馈通知的管理员账号 ID 列表', 0, NOW(), NOW(), 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `system_config` WHERE `config_key` = 'feedback.notify_admin_ids');
