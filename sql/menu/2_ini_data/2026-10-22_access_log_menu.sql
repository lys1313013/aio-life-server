-- 目的：补齐访问日志管理员菜单，供动态路由注册 /system/access-log。
-- 前置：已建立 sys_menu 表及 mobile_is_enabled 列，并配置 /system 顶级菜单。
-- 影响：仅新增缺失菜单；沿用历史访问日志的管理员权限与双端启用状态，不回填业务数据。
-- 重跑：按 ID、名称或路径判重，保留已有角色、开关、排序及逻辑删除状态。
-- 执行：按 upgrade-plan.json 顺序执行，由调用方选择目标库，遇错停止。
-- 恢复：记录本次新增 ID；需撤回时通过菜单管理停用该记录，不修改历史日志。
INSERT INTO `sys_menu`
  (`id`, `parent_id`, `name`, `path`, `component`, `meta`, `roles`, `sort_order`, `web_is_enabled`, `mobile_is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 1907, parent.id, 'AccessLog', '/system/access-log', 'system/access-log/index',
  JSON_OBJECT('icon', 'mdi:login-variant', 'title', '访问日志'),
  'admin', 12, 1, 1, NOW(), NOW(), 0
FROM sys_menu parent
WHERE parent.path = '/system' AND parent.parent_id = 0 AND parent.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 1907 OR m.name = 'AccessLog' OR m.path = '/system/access-log')
LIMIT 1;
