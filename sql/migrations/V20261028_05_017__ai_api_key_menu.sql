-- 目的：补齐当前菜单；MySQL 5.7.44 / 8.0，已完成当前结构迁移。
-- 影响：只补缺失项，按 ID、名称或路径判重，保留管理员修改和软删除状态。
-- 重跑：不恢复已有菜单；未知 SQL 错误直接失败，目标库由 Flyway 连接指定。
-- 复用连接器所在目录，兼容已经通过菜单管理改名或修改路径的 AI 接入目录。
INSERT INTO `sys_menu`
  (`id`, `parent_id`, `name`, `path`, `component`, `meta`, `roles`, `sort_order`, `web_is_enabled`, `mobile_is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 2304, tools.parent_id, 'mcpApiKeys', '/mcp/api-keys', 'mcp/api-keys/index',
  JSON_OBJECT('icon', 'ant-design:key-outlined', 'title', 'API Key 管理'),
  NULL, 1, 1, 1, NOW(), NOW(), 0
FROM sys_menu tools
JOIN sys_menu parent ON parent.id = tools.parent_id AND parent.is_deleted = 0
WHERE tools.path = '/mcp/tools' AND tools.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 2304 OR m.name = 'mcpApiKeys' OR m.path = '/mcp/api-keys')
LIMIT 1;

-- 沿用已有目录路径；仅为缺少标题的目录补默认值，不覆盖管理员填写的标题。
UPDATE sys_menu parent
JOIN sys_menu tools ON tools.parent_id = parent.id AND tools.path = '/mcp/tools' AND tools.is_deleted = 0
SET parent.meta = JSON_SET(COALESCE(parent.meta, JSON_OBJECT()), '$.title', 'AI 接入'),
    parent.update_time = NOW()
WHERE parent.is_deleted = 0
  AND (JSON_EXTRACT(parent.meta, '$.title') IS NULL
    OR JSON_TYPE(JSON_EXTRACT(parent.meta, '$.title')) = 'NULL'
    OR JSON_UNQUOTE(JSON_EXTRACT(parent.meta, '$.title')) = '');
