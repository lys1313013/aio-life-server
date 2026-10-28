-- 目的：补齐当前菜单；MySQL 5.7.44 / 8.0，已完成当前结构迁移。
-- 影响：只补缺失项，按 ID、名称或路径判重，保留管理员修改和软删除状态。
-- 重跑：不恢复已有菜单；未知 SQL 错误直接失败，目标库由 Flyway 连接指定。
INSERT INTO `sys_menu`
    (`id`, `parent_id`, `name`, `path`, `component`, `meta`, `roles`, `sort_order`, `web_is_enabled`, `mobile_is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 1512, parent.id, 'LifeFootprints', '/record/life-footprints', 'my-hub/life-footprints/index',
    JSON_OBJECT('icon', 'mdi:map-marker-path', 'title', '生活足迹', 'backTop', FALSE),
    NULL, 2, 1, 1, NOW(), NOW(), 0
FROM sys_menu parent
WHERE parent.path = '/record' AND parent.parent_id = 0 AND parent.is_deleted = 0
    AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 1512 OR m.name = 'LifeFootprints' OR m.path = '/record/life-footprints')
LIMIT 1;
