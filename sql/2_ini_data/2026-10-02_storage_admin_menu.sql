USE `aio_life`;

INSERT INTO `sys_menu`
  (`id`, `parent_id`, `name`, `path`, `component`, `meta`, `roles`, `sort`, `status`, `create_time`, `update_time`, `is_deleted`)
SELECT 1908, parent.id, 'StorageAdmin', '/system/storage', 'system/storage/index',
  JSON_OBJECT('icon', 'lucide:folder', 'title', '对象存储', 'authority', JSON_ARRAY('admin')),
  'admin', 7, 1, NOW(), NOW(), 0
FROM sys_menu parent
WHERE parent.name = 'System' AND parent.parent_id = 0 AND parent.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 1908 OR m.name = 'StorageAdmin' OR m.path = '/system/storage')
LIMIT 1;
