-- 目的：修复对象存储与定时任务菜单共用 1908，导致后者在新库中缺失的问题。
-- 前置：MySQL 5.7.44 / 8.0，已完成 20261028.05 系列迁移；sys_menu 已含两端启用字段。
-- 影响：补缺定时任务菜单使用 ID 1912，反向冲突时补缺对象存储使用 1913；保留所有已有菜单 ID、配置和软删除状态。
-- 执行：通过 Flyway 在无并发菜单维护的窗口执行，遇错停止；保留已发布迁移不变。
-- 重跑：按业务名称或路径判重；若补缺所需的 1912/1913 被无关菜单占用，主键冲突直接失败，不静默跳过。
-- 恢复：核对新增菜单后通过菜单管理停用；不修改任务配置、执行记录或日志。
INSERT INTO `sys_menu`
  (`id`, `parent_id`, `name`, `path`, `component`, `meta`, `roles`, `sort_order`, `web_is_enabled`, `mobile_is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 1912, parent.id, 'ScheduledJobAdmin', '/system/scheduled-jobs', 'system/scheduled-jobs/index',
  JSON_OBJECT('icon', 'mdi:calendar-clock', 'title', '定时任务'),
  'admin', 13, 1, 0, NOW(), NOW(), 0
FROM sys_menu parent
WHERE parent.path = '/system' AND parent.parent_id = 0 AND parent.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.name = 'ScheduledJobAdmin' OR m.path = '/system/scheduled-jobs')
LIMIT 1;

-- 反向旧库场景：1908 已属于定时任务，历史对象存储迁移因 ID 冲突被跳过。
-- 保留旧菜单 ID 及所有引用，仅在对象存储业务菜单缺失时用独立 ID 1913 补齐。
-- 若 1913 被无关菜单占用同样直接失败；存在同名/同路径软删除菜单时不恢复。
INSERT INTO `sys_menu`
  (`id`, `parent_id`, `name`, `path`, `component`, `meta`, `roles`, `sort_order`, `web_is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 1913, parent.id, 'StorageAdmin', '/system/storage', 'system/storage/index',
  JSON_OBJECT('icon', 'lucide:folder', 'title', '对象存储', 'authority', JSON_ARRAY('admin')),
  'admin', 7, 1, NOW(), NOW(), 0
FROM sys_menu parent
WHERE parent.path = '/system' AND parent.parent_id = 0 AND parent.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.name = 'StorageAdmin' OR m.path = '/system/storage')
LIMIT 1;
