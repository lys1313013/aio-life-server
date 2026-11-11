-- 目的：为现有 AI 接入分组增加 Web 查询工作台、数据模型入口。
-- 前置：PostgreSQL 18+，已执行 V20261110_02_006 菜单种子及后续适用版本。
-- 影响：仅补菜单元数据，不修改业务表、不回填用户数据、不覆盖管理员配置或恢复软删除项。
-- 重跑：路径已存在时保留原记录；ID 冲突导致事务失败，排查后重新迁移，不吞掉冲突。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；失败事务回滚。
INSERT INTO sys_menu (
  id,
  parent_id,
  name,
  path,
  component,
  meta,
  sort_order,
  web_is_enabled,
  mobile_is_enabled,
  create_time,
  update_time,
  is_deleted
)
SELECT
  preset.id,
  parent.id,
  preset.name,
  preset.path,
  preset.component,
  preset.meta::JSONB,
  preset.sort_order,
  1,
  0,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP,
  0
FROM sys_menu parent
CROSS JOIN (VALUES
  (2305::BIGINT, 'QueryWorkbench', '/query/workbench', 'data-query/workbench/index',
    '{"icon":"mdi:database-search-outline","title":"查询工作台","keepAlive":false,"fullPathKey":false,"backTop":false}', 2),
  (2306::BIGINT, 'QueryModels', '/query/models', 'data-query/models/index',
    '{"icon":"lucide:library","title":"数据模型","keepAlive":false,"fullPathKey":false,"backTop":false}', 3)
) AS preset(id, name, path, component, meta, sort_order)
WHERE parent.path = '/ai'
  AND parent.is_deleted = 0
ON CONFLICT (path) DO NOTHING;
