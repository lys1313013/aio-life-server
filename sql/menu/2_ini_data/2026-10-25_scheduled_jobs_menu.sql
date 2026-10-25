-- 目的：新增 Web 定时任务管理员菜单。
-- 前置：MySQL 5.7.44 或 8.0，sys_menu 已含 mobile_status，存在 /system 顶级菜单。
-- 影响：只补缺失菜单，不修改业务数据，移动端不开启此入口。
-- 重跑：按 ID、名称、路径判重，保留管理员修改和软删除状态。
-- 执行：按 upgrade-plan.json 顺序执行，遇错停止。
-- 恢复：记录新增菜单ID，回退时通过菜单管理停用，保留任务配置与日志。
INSERT INTO `sys_menu`
  (`id`, `parent_id`, `name`, `path`, `component`, `meta`, `roles`, `sort`, `status`, `mobile_status`, `create_time`, `update_time`, `is_deleted`)
SELECT 1908, parent.id, 'ScheduledJobAdmin', '/system/scheduled-jobs', 'system/scheduled-jobs/index',
  JSON_OBJECT('icon', 'mdi:calendar-clock', 'title', '定时任务'),
  'admin', 13, 1, 0, NOW(), NOW(), 0
FROM sys_menu parent
WHERE parent.path = '/system' AND parent.parent_id = 0 AND parent.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 1908 OR m.name = 'ScheduledJobAdmin' OR m.path = '/system/scheduled-jobs')
LIMIT 1;
