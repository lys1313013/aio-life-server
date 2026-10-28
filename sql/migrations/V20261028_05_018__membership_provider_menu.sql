-- 目的：补齐当前菜单；MySQL 5.7.44 / 8.0，已完成当前结构迁移。
-- 影响：只补缺失项，按 ID、名称或路径判重，保留管理员修改和软删除状态。
-- 重跑：不恢复已有菜单；未知 SQL 错误直接失败，目标库由 Flyway 连接指定。
-- 会员平台管理菜单；平台预设在 V20261028_05_010__membership_providers.sql 中维护。
INSERT INTO `sys_menu`
    (`id`, `parent_id`, `name`, `path`, `component`, `meta`, `roles`, `sort_order`, `web_is_enabled`, `mobile_is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 1911, parent.id, 'MembershipProviderAdmin', '/system/membership-providers', 'system/membership-providers/index',
    JSON_OBJECT('icon', 'lucide:crown', 'title', '会员平台', 'authority', JSON_ARRAY('admin')),
    'admin', 10, 1, 1, NOW(), NOW(), 0
FROM sys_menu parent
WHERE parent.name = 'System' AND parent.parent_id = 0 AND parent.is_deleted = 0
    AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 1911 OR m.name = 'MembershipProviderAdmin' OR m.path = '/system/membership-providers')
LIMIT 1;
