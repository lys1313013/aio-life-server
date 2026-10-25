USE `aio_life`;

-- 仅新增记录目录下的一个页面，不新增看板/书架/笔记子菜单。
-- 通过现有目录名称/路径解析父菜单，重复执行不重复插入。
INSERT INTO `sys_menu`
  (`id`, `parent_id`, `name`, `path`, `component`, `redirect`, `meta`, `roles`, `sort`, `status`, `create_time`, `update_time`, `is_deleted`)
SELECT 2056010094796967939, parent.id, 'Weread', '/record/weread', 'my-hub/weread/index', NULL,
  JSON_OBJECT('icon', 'mdi:book-open-variant', 'title', '微信读书', 'backTop', false),
  NULL, 9, 1, NOW(), NOW(), 0
FROM `sys_menu` parent
WHERE parent.name = 'Demos' AND parent.parent_id = 0 AND parent.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` m WHERE m.name = 'Weread' OR m.path = '/record/weread')
LIMIT 1;
