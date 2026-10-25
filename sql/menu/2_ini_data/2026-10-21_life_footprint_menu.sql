USE `aio_life`;

INSERT INTO `sys_menu`
    (`id`, `parent_id`, `name`, `path`, `component`, `meta`, `roles`, `sort`, `status`, `mobile_status`, `create_time`, `update_time`, `is_deleted`)
SELECT 1512, parent.id, 'LifeFootprints', '/record/life-footprints', 'my-hub/life-footprints/index',
    JSON_OBJECT('icon', 'mdi:map-marker-path', 'title', '生活足迹', 'backTop', FALSE),
    NULL, 2, 1, 1, NOW(), NOW(), 0
FROM sys_menu parent
WHERE parent.path = '/record' AND parent.parent_id = 0 AND parent.is_deleted = 0
    AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 1512 OR m.name = 'LifeFootprints' OR m.path = '/record/life-footprints')
LIMIT 1;
