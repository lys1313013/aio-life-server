USE `aio_life`;

-- 操作日志 Web 管理入口；只补缺失记录，不覆盖已有配置或删除状态。
INSERT INTO `sys_menu`
    (`id`, `parent_id`, `name`, `path`, `component`, `meta`, `roles`, `sort_order`, `web_is_enabled`, `mobile_is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 1906, parent.id, 'OperationLog', '/system/operation-log', 'system/operation-log/index',
    JSON_OBJECT('icon', 'mdi:clipboard-text-clock-outline', 'title', '操作日志'),
    'admin', 11, 1, 0, NOW(), NOW(), 0
FROM sys_menu parent
WHERE parent.path = '/system' AND parent.parent_id = 0 AND parent.is_deleted = 0
    AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 1906 OR m.name = 'OperationLog' OR m.path = '/system/operation-log')
LIMIT 1;
