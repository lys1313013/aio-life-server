USE `aio_life`;

-- 会员平台管理菜单；平台预设在 2026-10-20_membership_providers.sql 中维护。
INSERT INTO `sys_menu`
    (`id`, `parent_id`, `name`, `path`, `component`, `meta`, `roles`, `sort_order`, `web_is_enabled`, `mobile_is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 1911, parent.id, 'MembershipProviderAdmin', '/system/membership-providers', 'system/membership-providers/index',
    JSON_OBJECT('icon', 'lucide:crown', 'title', '会员平台', 'authority', JSON_ARRAY('admin')),
    'admin', 10, 1, 1, NOW(), NOW(), 0
FROM sys_menu parent
WHERE parent.name = 'System' AND parent.parent_id = 0 AND parent.is_deleted = 0
    AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 1911 OR m.name = 'MembershipProviderAdmin' OR m.path = '/system/membership-providers')
LIMIT 1;
