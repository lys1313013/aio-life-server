USE `aio_life`;

-- 复用连接器所在目录，兼容已经通过菜单管理改名或修改路径的 AI 接入目录。
INSERT INTO `sys_menu`
  (`id`, `parent_id`, `name`, `path`, `component`, `meta`, `roles`, `sort`, `status`, `mobile_status`, `create_time`, `update_time`, `is_deleted`)
SELECT 2304, tools.parent_id, 'mcpApiKeys', '/mcp/api-keys', 'mcp/api-keys/index',
  JSON_OBJECT('icon', 'ant-design:key-outlined', 'title', 'API Key 管理'),
  NULL, 1, 1, 1, NOW(), NOW(), 0
FROM sys_menu tools
JOIN sys_menu parent ON parent.id = tools.parent_id AND parent.is_deleted = 0
WHERE tools.path = '/mcp/tools' AND tools.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 2304 OR m.name = 'mcpApiKeys' OR m.path = '/mcp/api-keys')
LIMIT 1;

-- 旧种子目录沿用已有路径，避免破坏菜单锁和收藏；仅修正历史默认名称。
UPDATE sys_menu parent
JOIN sys_menu tools ON tools.parent_id = parent.id AND tools.path = '/mcp/tools' AND tools.is_deleted = 0
SET parent.meta = JSON_SET(COALESCE(parent.meta, JSON_OBJECT()), '$.title', 'AI 接入'),
    parent.update_time = NOW()
WHERE parent.is_deleted = 0
  AND JSON_UNQUOTE(JSON_EXTRACT(parent.meta, '$.title')) IN ('研发管理', 'MCP');
