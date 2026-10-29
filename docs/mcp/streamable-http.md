# MCP Streamable HTTP 接入说明

## 端点信息

- MCP 端点：`/api/mcp`
- 传输协议：`streamable-http`
- 认证方式：复用现有接口认证
  - `Authorization: Bearer <token>`
  - 或 `Authorization: Bearer <api-key>`

## 工具目录与结果

当前源码包含 16 个工具，覆盖时迹、待办、闪念、首页卡片、阅读、观影、目标、纪念日、B 站及豆瓣想读／想看；完整清单和现存契约差异见[接口范围](../../../docs/需求/MCP接口需求方案.md)。实际部署版本通过 `tools/list` 发现并读取 `inputSchema`，不把下方的想法示例视为唯一工具。

API Key 同样放在 `Authorization: Bearer <api-key>`，不是另一个未指明的自定义头。`tools/call` 检查 `isError` 及 `content` 中的业务结果；MCP 不使用 REST 的统一响应外壳。会话 ID 不能代替 Bearer 身份。

可选参数没有显式默认值时保持省略，不将枚举第一项当作默认值。分页使用 `page/pageSize`；所有 ID 入参为字符串。`task_list` 返回 `items/total`，应按总数继续翻页。新增时迹补记历史日期时需同时提供起止分钟。详细业务规则以工具说明和 `inputSchema` 为准。

## 调试示例

建议使用支持 Streamable HTTP 的 MCP 客户端。手动 curl 时先调用 `initialize`，按实际响应协商协议；若响应含 `Mcp-Session-Id`，后续请求必须携带该头和协商的 `MCP-Protocol-Version`，并发送 `notifications/initialized`。下方正文仅展示各请求形态，按服务返回补齐会话头；不要照搬独立 Query 服务的无会话假设。

### 初始化

```bash
curl -X POST 'http://localhost:45678/api/mcp' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'Authorization: Bearer <token>' \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2025-03-26",
      "capabilities": {},
      "clientInfo": {
        "name": "curl-client",
        "version": "1.0.0"
      }
    }
  }'
```

### 查询工具列表

```bash
curl -X POST 'http://localhost:45678/api/mcp' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'Authorization: Bearer <token>' \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list",
    "params": {}
  }'
```

### 调用想法保存工具

此请求会新增业务数据，仅在明确需要保存时调用；测试使用隔离账号与虚构内容。

```bash
curl -X POST 'http://localhost:45678/api/mcp' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'Authorization: Bearer <token>' \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "thought_save",
      "arguments": {
        "content": "今天完成了 MCP 接入",
        "events": [
          {
            "content": "新增 streamable-http MCP server"
          }
        ]
      }
    }
  }'
```

## 新增工具

1. 定义 `@McpToolProvider` 类，在方法上使用 LangChain4j `@Tool`，参数 DTO 字段使用 `@Description`。
2. 注册器 `McpToolRegistry` 自动扫描；当前方法只允许零参数或单参数，不依赖 Controller 的 `@McpOperation`。
3. 复用已有 Service／Controller 业务规则，确保当前用户、菜单二级锁、逻辑删除及关联归属一致。
4. 通过 `tools/list` 核对名称及 Schema，并验证成功、错误、用户隔离和长 ID。源码注册成功不等于真实客户端或发布环境已验证。
