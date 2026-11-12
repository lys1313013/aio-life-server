-- 目的：个人分类统一从个人中心／“我”的分类管理进入，公共时迹分类移入系统管理。
-- 前置：PostgreSQL 18+；已执行 V20261112_14，菜单含当前个人中心、系统管理和两个个人分类入口。
-- 影响：仅新增隐藏的个人分类路由分组并调整菜单归属、名称和排序；保留原菜单ID、路径、启用状态及用户菜单锁、收藏和快捷导航配置，不回填业务数据。
-- 重跑：Flyway 成功后不重跑；手工重跑按路径补缺分组，并将指定入口调整到本次定义的位置。
-- 恢复：事务内失败自动回滚；不修改已执行迁移。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate，遇错停止。
-- 验证：个人分类父级为 /profile/category-management；管理员时迹分类父级为 /system 且仅 admin 可见。

INSERT INTO sys_menu (id, parent_id, name, path, component, meta, roles, sort_order, web_is_enabled, mobile_is_enabled)
SELECT 2201, 0, 'ProfileCategoryManagement', '/profile/category-management', 'BasicLayout',
       '{"title":"分类管理","icon":"mdi:tag-multiple-outline","hideInMenu":true}'::JSONB, NULL, 9997, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/profile/category-management' AND is_deleted = 0);

UPDATE sys_menu
SET parent_id = (SELECT id FROM sys_menu WHERE path = '/profile/category-management' AND is_deleted = 0),
    meta = jsonb_set(COALESCE(meta, '{}'::JSONB), '{title}', '"通用分类"'::JSONB),
    sort_order = 0, roles = NULL, update_time = CURRENT_TIMESTAMP
WHERE path = '/my-hub/exercise/category-config' AND is_deleted = 0;

UPDATE sys_menu
SET parent_id = (SELECT id FROM sys_menu WHERE path = '/profile/category-management' AND is_deleted = 0),
    meta = jsonb_set(COALESCE(meta, '{}'::JSONB), '{title}', '"时迹分类"'::JSONB),
    sort_order = 1, update_time = CURRENT_TIMESTAMP
WHERE path = '/time/my-categories' AND is_deleted = 0;

UPDATE sys_menu
SET parent_id = (SELECT id FROM sys_menu WHERE path = '/system' AND is_deleted = 0),
    meta = jsonb_set(COALESCE(meta, '{}'::JSONB), '{title}', '"时迹分类"'::JSONB),
    roles = 'admin', sort_order = 14, update_time = CURRENT_TIMESTAMP
WHERE path = '/time/category-admin' AND is_deleted = 0;
