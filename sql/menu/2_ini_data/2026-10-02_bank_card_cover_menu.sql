USE `aio_life`;

INSERT INTO `sys_menu`
  (`id`, `parent_id`, `name`, `path`, `component`, `meta`, `roles`, `sort`, `status`, `create_time`, `update_time`, `is_deleted`)
SELECT 1909, parent.id, 'BankCardCoverAdmin', '/system/bank-card-covers', 'system/bank-card-covers/index',
  JSON_OBJECT('icon', 'lucide:credit-card', 'title', '银行卡卡面', 'authority', JSON_ARRAY('admin')),
  'admin', 8, 1, NOW(), NOW(), 0
FROM sys_menu parent
WHERE parent.name = 'System' AND parent.parent_id = 0 AND parent.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 1909 OR m.name = 'BankCardCoverAdmin' OR m.path = '/system/bank-card-covers')
LIMIT 1;
