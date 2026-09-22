USE `aio_life`;

INSERT INTO `sys_menu`
  (`id`, `parent_id`, `name`, `path`, `component`, `meta`, `roles`, `sort`, `status`, `create_time`, `update_time`, `is_deleted`)
SELECT 1906, parent.id, 'OperationLog', '/system/operation-log', 'system/operation-log/index',
  JSON_OBJECT('icon', 'mdi:clipboard-text-clock-outline', 'title', '操作日志'),
  'admin', 5, 1, NOW(), NOW(), 0
FROM sys_menu parent
WHERE parent.name = 'System' AND parent.parent_id = 0 AND parent.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 1906 OR m.name = 'OperationLog' OR m.path = '/system/operation-log')
LIMIT 1;

INSERT INTO `sys_menu`
  (`id`, `parent_id`, `name`, `path`, `component`, `meta`, `roles`, `sort`, `status`, `create_time`, `update_time`, `is_deleted`)
SELECT 1907, parent.id, 'AccessLog', '/system/access-log', 'system/access-log/index',
  JSON_OBJECT('icon', 'mdi:login-variant', 'title', '访问日志'),
  'admin', 6, 1, NOW(), NOW(), 0
FROM sys_menu parent
WHERE parent.name = 'System' AND parent.parent_id = 0 AND parent.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 1907 OR m.name = 'AccessLog' OR m.path = '/system/access-log')
LIMIT 1;
