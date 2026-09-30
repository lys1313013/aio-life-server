# AI 接口调用指南

后端使用 `springdoc-openapi` 从当前运行的 Controller、请求/响应类型及 JavaDoc 生成 OpenAPI 3.0 文档。AI 应先读取文档，再使用 HTTP 工具调用接口。文档本身不会为 AI 安装 HTTP 工具，也不替代现有 MCP。

## 渐进式读取（推荐）

文档接口均可匿名访问，无需 API Key。先查目录和摘要，只在选中接口后读取详情，避免把整个模块的 Schema 放入 AI 上下文。

| 层级 | URL（默认本地地址） | `data` 返回内容 |
| --- | --- | --- |
| 目录 | `http://localhost:45678/api/docs/catalog` | `modules`：模块 `id`、`name`、`description`、`operationCount`；`operationCount`：全部接口数 |
| 搜索 | `http://localhost:45678/api/docs/operations?keyword=目标&module=record&page=1&pageSize=20` | `items`：`operationId`、`name`、`method`、`path`、`modules`；分页 `total`、`page`、`pageSize` |
| 详情 | `http://localhost:45678/api/docs/operations/get_goals` | `operationId`、`method`、`path`、`modules`、`definition` |

所有响应沿用 `{ "rscode": "0", "result": null, "data": ... }`。搜索摘要不携带参数和 Schema；`definition` 是可独立解析的单接口 OpenAPI 文档，包含参数、请求体、响应、鉴权及递归引用的必要组件，循环引用保留 `$ref`。

查询约定：

- `keyword` 可选，匹配接口名称、说明、路径、HTTP 方法、`operationId` 和标签；支持中文，英文忽略大小写，最多 200 字符。
- `module` 可选，使用目录返回的模块 `id`；不传时搜索全部模块。
- `page` 默认 1，正整数；`pageSize` 默认 20，范围 1–100。按 `operationId` 升序分页，无结果或超出末页时返回空 `items`。`total` 遵循项目 Long 序列化规则，是字符串。
- 参数不合法或模块不存在返回 HTTP 400；接口标识不存在返回 HTTP 404，错误响应仍使用统一响应结构。
- 详情的 `definition.servers[0].url` 为当前上下文相对地址，默认 `/api`；与实际服务域名和 `path` 拼接使用。

```bash
export AIO_LIFE_API_BASE_URL='http://localhost:45678/api'

# 1. 读取模块目录
curl --fail-with-body --silent --show-error "${AIO_LIFE_API_BASE_URL}/docs/catalog"

# 2. 搜索目标接口，只读摘要
curl --fail-with-body --silent --show-error --get \
  --data-urlencode 'keyword=目标' --data-urlencode 'module=record' \
  --data-urlencode 'page=1' --data-urlencode 'pageSize=10' \
  "${AIO_LIFE_API_BASE_URL}/docs/operations"

# 3. 使用搜索结果中的 operationId 获取详情
curl --fail-with-body --silent --show-error \
  "${AIO_LIFE_API_BASE_URL}/docs/operations/get_goals"
```

索引由 Springdoc 实际输出生成，模块归属和接口数量不另行维护。首次访问在服务端构建并缓存索引，客户端仍只接收当前层级内容；后续访问复用缓存。重启或开发热重载后重新生成；配置 `springdoc.cache.disabled=true` 时每次重新生成。禁用业务模块时，目录可能保留接口数量为 0 的模块。

## 完整文档（兼容入口）

以默认本地地址 `http://localhost:45678/api` 为例：

| 用途 | 路径 |
| --- | --- |
| 全部已启用接口，JSON | `/api/v3/api-docs` |
| 全部已启用接口，YAML | `/api/v3/api-docs.yaml` |
| 指定模块，JSON | `/api/v3/api-docs/{group}` |
| 指定模块，YAML | `/api/v3/api-docs.yaml/{group}` |

`group` 可选：`record`（时迹、目标、阅读等记录）、`sso`（认证、用户、文件）、`system`（系统管理）、`wardrobe`、`membership`、`bankcard`、`feedback`、`relationship`、`llm`、`mcp`。关系图谱关闭时，该模块没有业务路由。

文档可直接通过浏览器或 HTTP 工具匿名读取，无需登录 Token 或 API Key。业务接口仍沿用现有鉴权：使用登录 Token 或 `ak-` 开头的 API Key，通过 `Authorization: Bearer ...` 传递，不使用独立的 `X-API-Key` 请求头；未登录调用受保护业务接口仍返回 HTTP 401。

```bash
# 获取文档无需任何凭据。
export AIO_LIFE_API_BASE_URL='http://localhost:45678/api'

curl --fail-with-body --silent --show-error \
  "${AIO_LIFE_API_BASE_URL}/v3/api-docs/record" \
  -o openapi-record.json

# 调用业务接口时，先设置 AIO_LIFE_API_TOKEN 为登录 Token 或 API Key。
# 根据文档查询进行中的目标。
curl --fail-with-body --silent --show-error --get \
  -H "Authorization: Bearer ${AIO_LIFE_API_TOKEN}" \
  --data-urlencode 'status=in_progress' \
  "${AIO_LIFE_API_BASE_URL}/goals"
```

默认启用文档，可通过 `AIO_LIFE_OPENAPI_ENABLED=false` 同时关闭完整文档与渐进文档接口；本次仅集成机器可读文档，不包含 Swagger UI。

## 可交给 AI 的调用规则

1. 优先读取 `/docs/catalog`，通过 `/docs/operations` 搜索，再按返回的 `operationId` 读取详情中的 `definition`。需要批量导入工具时再读取完整或模块文档。按 `paths`、HTTP 方法、`operationId`、`parameters`、`requestBody` 和响应 Schema 构造请求。解析 `#/components/schemas/...` 引用，不猜测缺失的业务字段。
2. 使用实际部署地址。文档的 `servers` 通常已包含 `/api`，`paths` 不含该前缀，拼接时不要重复添加。反向代理环境应核对实际外部地址。
3. 文档本身公开可读，文档中的 `security` 描述的是业务接口的鉴权要求。业务调用凭据由运行环境提供，不写入提示词、导出的文档或代码库。登录、注册和验证码等匿名接口标记 `security: []`；其他接口通常继承 Bearer 鉴权。文件接口即使支持匿名，也只对公开资源有效。
4. 普通 JSON 返回 `{ "rscode": "0", "result": null, "data": ... }`。只有 `rscode == "0"` 表示业务成功，HTTP 200 本身不足以判断。文件和流式接口按各自协议处理。
5. `113000` 为通用业务失败，`100400` 为参数错误；`2001` 表示需要二级密码验证，`data.menuPath` 为锁定模块。向用户说明并等待完成验证，不循环重试。HTTP 401 的响应可能是纯文本。
6. `Long/long`（ID、分页总数等）在 JSON 响应中为字符串。保留原字符串，不转换为可能丢失精度的 JavaScript Number。枚举传 Schema 中的真实值，如 `in_progress`。
7. GET 查询不发送请求体。分页和条件平铺，例如 `?page=1&pageSize=50&startDate=2026-09-01&endDate=2026-09-29`，不使用 `condition.startDate` 或 `condition={...}`。数组用重复键，例如 `statuses=in_progress&statuses=on_hold`。
8. 日期和时间按字段说明发送。带 `@JsonFormat` 的本地时间通常为 `yyyy-MM-dd HH:mm:ss`；未定制的 `LocalDateTime` 使用 ISO 本地时间，如 `2026-09-29T10:30:00`。不要自行补时区或改成时间戳。

## 维护方式与当前边界

- 新增 Controller/DTO 后会自动生成结构；JavaDoc 通过 `therapi-runtime-javadoc-scribe` 在编译时保留，字段和方法说明不需要维护第二份清单。
- 在 JavaDoc 中补齐用途、单位、前置条件；需要枚举范围、必填、示例等精确信息时使用 `@Operation`、`@Parameter`、`@Schema`，实际参数校验仍由业务代码及 Bean Validation 执行。
- `OpenApiConfig` 适配字符串 Long、`@JsonFormat`、平铺 `@QueryParams`、数组查询及匿名认证接口。HTTP 方法和路径生成稳定的 `operationId`，分组与全量文档保持一致。
- 本版覆盖当前启用 Controller 的结构与已有注释，补充了目标接口示例说明。历史代码中缺少注释的字段、`Map<String,Object>`/`Object` 动态结构、手工校验规则和流式响应仍需逐步补充，不能把自动生成文档等同于完整业务语义。
- MCP 的 `/mcp/tools` 可列出已注册工具的 `inputSchema`。OpenAPI 中 `/mcp/tools/call` 的 `arguments` 是动态对象，调用前必须读取工具列表。
- 文件预览使用 `/file/preview/{id}`。历史兼容入口 `/file/preview/{*fileName}` 仍可运行，但因与标准入口形成冲突的 OpenAPI 路径模板，仅在文档中隐藏。

## 本地验证

```bash
mvn -Dtest=OpenApiIntegrationTest,OpenApiOperationExtractorTest,JsonConfigTest,QueryHttpContractTest test
```

测试加载真实 Controller、springdoc 和认证拦截器，模拟业务依赖及登录身份，不访问数据库、Redis、MinIO。验证路由覆盖、操作标识唯一、JSON/YAML、模块分组、文档匿名访问、业务接口未登录拦截、泛型响应、字符串 ID、状态枚举、日期格式和查询参数，以及渐进文档的搜索分页、参数边界、全部操作的单接口引用完整性、循环和多态 Schema 裁剪、关闭文档开关，并将测试环境文档输出到 `target/openapi/openapi.json`。该文件是本地契约检查产物，实际部署的可用模块和接口应以运行时文档为准。
