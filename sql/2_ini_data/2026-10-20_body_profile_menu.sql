USE `aio_life`;

-- 身体数据作为「记录」下的独立业务菜单，两端均可见；重复执行保留已有菜单配置。
INSERT INTO `sys_menu`
    (`id`, `parent_id`, `name`, `path`, `component`, `meta`, `roles`, `sort`, `status`, `mobile_status`, `create_time`, `update_time`, `is_deleted`)
SELECT 1511, parent.id, 'BodyProfile', '/record/body-profile', 'my-hub/body-profile/index',
    JSON_OBJECT('icon', 'mdi:human-handsup', 'title', '身体数据', 'backTop', FALSE),
    NULL, 1, 1, 1, NOW(), NOW(), 0
FROM sys_menu parent
WHERE parent.path = '/record' AND parent.parent_id = 0 AND parent.is_deleted = 0
    AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 1511 OR m.name = 'BodyProfile' OR m.path = '/record/body-profile')
LIMIT 1;
