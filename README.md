# AIO Life Server

AIO Life 的业务后端，提供生活记录、目标与待办、财务与物品管理、第三方同步、账号权限和 MCP 工具。Web、移动端和浏览器插件共用此服务；独立只读查询服务另见 [aio-life-query](https://github.com/lys1313013/aio-life-query)。

- [项目总览与跨端文档](https://github.com/lys1313013/aio-life)
- [后端文档目录](docs/README.md) · [开发规范](AGENTS.md)
- [HTTP 接口约定](docs/HTTP接口请求约定.md) · [OpenAPI 调用指南](docs/AI接口调用指南.md) · [MCP 接入](docs/mcp/streamable-http.md)

## 环境与依赖

| 依赖 | 要求与用途 |
| --- | --- |
| Java / Maven | JDK 21；构建镜像使用 Maven 3.9，依赖版本以 [pom.xml](pom.xml) 为准 |
| MySQL | 仅支持 8.x（最低 8.0.16）；迁移及 CI 业务测试覆盖 8.0.40 / 8.4.2，具体边界见 [SQL 规范](docs/SQL脚本规范.md) |
| Redis | 登录会话、二级锁解锁状态、缓存、分布式锁和定时任务需要 |
| MinIO | 附件、头像、封面、导出等文件功能需要；不使用文件功能时可暂不配置 |
| SMTP | 邮箱验证码、注册及邮件相关功能需要；默认配置为 QQ 邮箱 SMTP |
| Neo4j | 人际关系图谱需要，默认关闭 |

核心技术为 Spring Boot 3.5.16、MyBatis-Plus、Sa-Token JWT、Redis / Caffeine、MinIO、MapStruct 和 Log4j2；使用 Micrometer + Brave 提供指标与链路日志。详细版本统一查看 `pom.xml`，不在此重复维护完整依赖清单。

## 本地启动

### 1. 获取代码

```bash
git clone https://github.com/lys1313013/aio-life-server.git
cd aio-life-server
java -version
mvn -version
```

已有 AIO Life 主仓库时，可使用主仓库的 `scripts/setup-repositories.sh` 初始化 Web、后端和移动端。

### 2. 准备数据库

先准备 MySQL 和 Redis，再按[数据库初始化与验证](docs/数据库初始化与验证.md)创建 `aio_life` 数据库并完成结构和种子初始化。应用数据源用户名为 `aio_life`，地址变量只填 `host:port`；库名和用户名由 [application.yml](src/main/resources/application.yml)配置，不能把初始化用的 root 密码误当作应用账号密码。

数据库迁移通过独立 Flyway 工具执行，需要 Maven 和 Java 21。连接通过 `FLYWAY_URL`、`FLYWAY_USER`、`FLYWAY_PASSWORD` 环境变量注入；创建好空库后，在后端仓库执行：

```bash
mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate
```

117 已有库已按用户确认登记至 `20261028.06`，现有 SQL 不再重跑，后续只执行更高版本 V；[接入状态与结构差异](docs/数据库初始化与验证.md#已有库首次接入)分别记录，登记不等于结构完全一致。结构预检使用 v5 契约，格式不匹配时重新生成；Java 注释或测试依赖改动不使契约失效。后续使用同一入口的 `info`、`migrate`、`validate` 管理升级；应用启动不会自动迁移数据库。

日常结构和默认数据变更新增不可改写的 `V` 迁移；维护者在选定节点生成新的完整 `B` 基线。新库不再创建固定密码管理员，先通过注册流程建立账号，再由受控管理流程授权。

### 3. 配置环境

Maven、`java -jar` 和 `start.sh` 都不会自动加载 `.env`。启动前在当前终端或进程管理器注入环境变量；下列值须按本机配置填写：

```bash
export AIO_LIFE_DB_URL='127.0.0.1:3306'
export AIO_LIFE_DB_PASSWORD='<应用数据库账号密码>'
export AIO_LIFE_REDIS_HOST='127.0.0.1'
export AIO_LIFE_REDIS_PORT='6379'
export AIO_LIFE_REDIS_PASSWORD=''
export AIO_LIFE_JWT_SECRET='<独立的随机签名密钥>'
export AIO_LIFE_NOTIFICATION_ENCRYPTION_KEY='<至少16位的稳定加密主密钥>'
export AIO_LIFE_SERVER_BASE_URL='http://localhost:45678/api'
```

| 配置 | 使用场景 |
| --- | --- |
| `AIO_LIFE_MINIO_ENDPOINT`、`AIO_LIFE_MINIO_ACCESS_KEY`、`AIO_LIFE_MINIO_SECRET_KEY`、`AIO_LIFE_MINIO_BUCKET_NAME` | 文件存储；默认地址 `http://localhost:1300`，业务桶 `aiolife`，需准备可访问的桶与相应权限 |
| `AIO_LIFE_MAIL_USERNAME`、`AIO_LIFE_MAIL_PASSWORD` | 邮箱账号与 SMTP 授权凭据；其他供应商需同时覆盖 `spring.mail.*` |
| `AIO_LIFE_BANK_CARD_ENCRYPTION_KEY` | 保存或读取卡号需要；值为 **32 字节随机数据的 Base64 编码**，不是任意 32 位文本 |
| `AIO_LIFE_NOTIFICATION_ENCRYPTION_KEY` | 加密通知凭据、微信 AppSecret、地图及其他系统配置密钥；至少 16 位，保存加密数据后应保持稳定 |
| `AIO_LIFE_NEO4J_ENABLED`、`AIO_LIFE_NEO4J_URI`、`AIO_LIFE_NEO4J_USERNAME`、`AIO_LIFE_NEO4J_PASSWORD` | 启用并连接关系图谱；开关默认 `false` |
| `AIO_LIFE_SCHEDULING_ENABLED` | 定时调度总开关，默认 `true`；具体任务在 Web「系统管理 → 定时任务」维护 |
| `AIO_LIFE_OPENAPI_ENABLED` | 机器可读 API 文档开关，默认 `true` |
| `AIO_LIFE_QUERY_ACCESS_ENABLED`、`AIO_LIFE_QUERY_ACCESS_SERVICE_KEY`、`AIO_LIFE_QUERY_ACCESS_ALLOWED_APPS` | 对接独立 Query 服务；默认关闭，按 [Query README](https://github.com/lys1313013/aio-life-query/blob/main/README.md)配置双方凭据 |

完整配置与默认值见 [application.yml](src/main/resources/application.yml)。真实密码、Token 和密钥不写入仓库。部署时替换开发默认密钥，保存好银行卡及通知加密主密钥；直接更换会使已有密文无法解密。公网 `AIO_LIFE_SERVER_BASE_URL` 必须包含 `/api`，例如 `https://aiolife.top/api`。

### 4. 启动与确认

```bash
mvn spring-boot:run
```

也可使用 `./start.sh`，先检查 MySQL / Redis 和已启用的 Neo4j 端口，再启动应用；它不创建数据库、不执行迁移、不拉起依赖容器。`./start.sh --clean` 会先清理编译产物，`SKIP_DEPS_CHECK=true` 可跳过端口检查。

| 入口 | 默认地址 |
| --- | --- |
| 业务 API 前缀 | `http://localhost:45678/api` |
| API 模块目录 | `http://localhost:45678/api/docs/catalog` |
| 完整 OpenAPI JSON | `http://localhost:45678/api/v3/api-docs` |
| MCP Streamable HTTP | `http://localhost:45678/api/mcp` |
| 健康检查 | `http://localhost:45679/actuator/health` |
| Prometheus 指标 | `http://localhost:45679/actuator/prometheus` |

```bash
curl --fail --silent --show-error http://localhost:45679/actuator/health
curl --fail --silent --show-error http://localhost:45678/api/docs/catalog
```

`/api` 是业务前缀，不是首页。OpenAPI 文档默认匿名可读，仓库未集成 Swagger UI。健康检查成功不代表所有可选服务和业务功能已完成联调。

## 开发与验证

以下命令在本仓库执行：

```bash
mvn test                                      # 默认排除 external 组
mvn test -Dtest=TimeTrackerCategoryControllerIntegrationTest
./test.sh TimeTrackerCategoryControllerIntegrationTest
mvn test -Pexternal-tests                      # 包含外部服务测试，按需使用
mvn clean package -DskipTests                  # 只打包，不代表测试通过
java -jar target/aio-life-server-1.0.0.jar
```

Surefire 默认使用 `test` 配置，部分集成测试仍需要已初始化的 MySQL / Redis；应指向隔离测试环境，不能因为启用 `test` profile 就认为数据访问已隔离。配置见 [application-test.yml](src/test/resources/application-test.yml)，CI 环境见 [docker-publish.yml](.github/workflows/docker-publish.yml)。数据库验证与增量测试另见[数据库初始化与验证](docs/数据库初始化与验证.md)，初始化检查通过不能替代已有库升级验证。

CI 通过独立 Flyway Maven 工具初始化业务测试库，并运行 Java 迁移测试；迁移检查与业务测试均通过后才发布镜像。

纯文档修改检查差异和链接，无需运行业务测试或构建。业务改动按影响范围验证；数据库、外部服务、真实设备及部署结果分别报告。

请求 DTO 变更后，从 AIO Life **主仓库**执行：

```bash
python3 scripts/generate-api-contracts.py
python3 scripts/generate-api-contracts.py --check
```

提交前阅读 [AGENTS.md](AGENTS.md)。跨仓库推送使用主仓库的 `python3 scripts/push-repositories.py`，前提与失败恢复见[统一推送说明](https://github.com/lys1313013/aio-life/blob/main/docs/API参数规范.md#本地统一推送与联合检查)。

## 接口约定

- 业务请求使用 `Authorization: Bearer <token>`；支持 API Key 的入口同样使用该请求头，具体权限以接口定义为准。
- 普通 JSON 响应为 `{ "code": 0, "message": null, "data": ... }`，只有整数 `code == 0` 表示业务成功。文件、流式响应和 MCP 按各自协议处理。
- ID 在客户端全程保留字符串；`Long/long` 默认输出字符串，分页 `total` 有字段级数字序列化，不凭字段名推断类型。
- 分页使用 `page/pageSize`，从第 1 页开始；日期、可空字段、数值范围及枚举按当前 DTO / OpenAPI 声明。
- 查接口先读 `/docs/catalog`，再用 `/docs/operations` 搜索并获取单接口详情；MCP 使用 `tools/list` 发现当前工具和 `inputSchema`。

完整约定见 [HTTP 接口请求约定](docs/HTTP接口请求约定.md)与 [AI 接口调用指南](docs/AI接口调用指南.md)。

## 使用 Docker 部署

先在容器可达的 MySQL / Redis 中准备业务账号和当前数据库结构。`.env` 使用不含 `export` 的 `KEY=value` 格式；容器内 `localhost` 指自身，依赖地址须对应实际容器网络或宿主机入口。

```bash
docker build -t aio-life-server:latest .
docker run -d --name aio-life-server \
  -p 45678:45678 \
  --env-file .env \
  aio-life-server:latest
```

[Dockerfile](Dockerfile)分阶段构建并以非 root 用户运行，构建阶段跳过测试。`JAVA_OPTS` 可覆盖 JVM 参数。示例只发布业务端口；管理端口 45679 按监控网络需要单独开放。

### SkyWalking Java Agent

[Dockerfile](Dockerfile)（源码构建）和 [Dockerfile.runtime](Dockerfile.runtime)（CI 已测试 JAR）均从固定版本及镜像摘要的 `apache/skywalking-java-agent:9.8.0-alpine` 复制完整 Agent 到 `/opt/skywalking-agent`，包含 `plugins`、`activations` 和配置。升级时同步修改两份文件中的版本及摘要。最终镜像继续使用原 Java 21 基础镜像、非 root 用户和 JVM 内存参数；Agent 默认不加载。

使用主仓库 Compose 时已经自动启用 Agent；下面的变量用于手动 `docker run`，已有 `JAVA_TOOL_OPTIONS` 时追加 Agent 参数。使用 Compose 时无需在 `.env` 重复填写 `-javaagent`：

```dotenv
JAVA_TOOL_OPTIONS=-javaagent:/opt/skywalking-agent/skywalking-agent.jar
SW_AGENT_NAME=aio-life-server
SW_AGENT_COLLECTOR_BACKEND_SERVICES=skywalking-oap:11800
SW_AGENT_SAMPLE=-1
SW_LOGGING_OUTPUT=CONSOLE
```

`SW_AGENT_SAMPLE=-1` 表示不限制采样数量，需配合 OAP 的 100% 采样策略；数据保留期在存储端配置。Agent 日志默认输出控制台，可通过 `docker logs` 查看。容器须能解析并连接 OAP 地址，容器内 `127.0.0.1` 指向自身。主仓库的 [docker-compose.yml](https://github.com/lys1313013/aio-life/blob/main/docker-compose.yml) 已合并 Agent 启用参数，并将后端同时接入业务网络和已有的 SkyWalking 网络，不需要额外 Compose 文件或宿主机 Agent 目录挂载。先启动 SkyWalking，再使用主仓库的 `docker compose up -d`；默认外部网络为 `skywalking_default`，可通过 `.env` 的 `SKYWALKING_NETWORK` 覆盖。

发布新镜像后重建后端容器才会生效。验收时先检查 Agent 加载及连接日志，再请求一个只读业务接口，确认 SkyWalking 中出现服务、接口链路和 JVM 指标，并检查业务接口响应。基础镜像验证应分别运行未启用和启用 Agent 的 Java 冒烟程序，检查非 root 身份、JVM 参数及 Agent 加载日志；业务链路另行验证。

现有日志 `traceId` / `spanId` 仍来自 Micrometer/Brave，与 SkyWalking Trace ID 不同。本改动仅提供 Agent 接入能力；日志关联需另外集成 Log4j2 Toolkit 并输出完整 SkyWalking Trace ID，不修改现有日志与追踪行为。官方参考：[容器接入](https://skywalking.apache.org/docs/skywalking-java/latest/en/setup/service-agent/java-agent/containerization/)、[Agent 配置](https://skywalking.apache.org/docs/skywalking-java/latest/en/setup/service-agent/java-agent/configurations/)。

### 其他容器编排

仓库中的 Compose 文件各自独立，不是一键启动整套业务的入口：

| 文件 | 内容与前提 |
| --- | --- |
| [docker/docker-compose.yml](docker/docker-compose.yml) | Prometheus / Grafana，不提供 MySQL、Redis 或业务应用 |
| [docker/docker-compose-minio.yml](docker/docker-compose-minio.yml) | MinIO；须先准备 `docker/.env` 中的 `MINIO_PORT`、`MINIO_CONSOLE_PORT`、`MINIO_USER`、`MINIO_PASSWORD`，从根目录运行时同时传 `--env-file docker/.env` |
| [docker/docker-compose-neo4j.yml](docker/docker-compose-neo4j.yml) | Neo4j；应用需另行开启图谱并匹配连接凭据 |

Compose 示例中的默认账号和密码仅供本地使用。镜像发布流程见 [GitHub Actions](.github/workflows/docker-publish.yml)；镜像推送不代表目标环境已部署。仓库没有 `deploy.sh`。

## 功能配置与运维入口

### 文件、头像与对象存储

- 头像保存 `avatar_file_id`，资料更新使用 `avatarFileId`：不传保持，`null` 清除。当前公开头像地址由 `AIO_LIFE_SERVER_BASE_URL` 生成，路径为 `/public/images/{fileId}.{ext}`；部署需保证此 URL 可达。MinIO 桶无需改为公开；普通附件仍通过文件接口按权限访问。
- 阅读、观影远程封面只允许配置白名单域名的 HTTPS 地址，不跟随重定向。`AIO_LIFE_DOUBAN_COVER_ALLOWED_DOMAINS` 默认 `doubanio.com`，信任其全部子域名；显式空值禁用远程下载。其他来源使用上传得到的 `fileId`。相关安全修复记录见[文件内容安全](docs/file-content-security.md)，接口行为以当前实现为准。
- Web「系统管理 → 对象存储」仅管理员可用，按前缀和游标浏览当前业务桶，支持预览、下载及删除无业务引用的单个对象。已有 `file` 记录（含软删除）或 CBTI 引用的对象不能删除；不提供上传、批量目录删除或桶策略修改。MinIO 凭据需具备 `ListBucket` / `GetObject` / `DeleteObject` 权限；入口种子为 `sql/migrations/V20261028_05_016__storage_admin_menu.sql`。

### 登录、隐私与通知

- 微信登录在「系统配置 → 微信小程序与订阅通知」维护 AppID / AppSecret；客户端不得保存 AppSecret。接入与发布顺序见[微信小程序登录](docs/微信小程序登录.md)。
- 微信订阅通知默认关闭，模板需匹配实际字段，配置及验收见[订阅消息接入](https://github.com/lys1313013/aio-life/blob/main/docs/微信小程序订阅消息接入.md)。
- 正式使用注册与账号注销前，填写真实运营主体和可收信邮箱；协议版本、注销清理与联调要求见[隐私政策与账号注销](docs/隐私政策与账号注销.md)。
- 验证码频率限制统一读取 `aio.life.server.auth.code.*`，由启动配置维护，不属于后台动态配置。

### 已登录 App 扫码登录 Web

Web 入口为 `/auth/app-qrcode-login`，已登录的 App / 微信小程序通过「我 → 扫码登录」扫描并核对四位数字后确认。此功能使用自有账号会话，与微信小程序码登录分别配置，不要求微信开放平台配置。

接口前缀 `/api/auth/qr-login`，只允许登录会话授权，API Key 不可代替用户确认。二维码有效期 120 秒；浏览器查询凭据使用 `X-QR-Secret` 请求头，兑换和撤销凭据使用请求体，不放入 URL 或持久化存储。完整请求字段查询 OpenAPI。

正式环境使用 HTTPS。发布顺序为后端、Web、移动端，Redis 须可用；反向代理保留 `Authorization` / `X-QR-Secret` 并禁用本组接口缓存。App 需重新打包扫码原生模块，H5 不提供摄像头扫码入口。专项测试为 `QrLoginHttpIntegrationTest`，真实设备验收需单独执行。

### 生活足迹

入口 `/record/life-footprints`，接口前缀 `/api/life-footprints`。起止日期使用 `yyyy-MM-dd`，结束日期必填，`9999-12-31` 表示至今；不使用 `ongoing`。暂不使用乐观锁，同账号多端编辑以最后成功保存为准；请求结果不确定时先重新读取确认。

Web 使用腾讯 GL，H5 使用 Leaflet 底图，微信和 App 使用原生 GCJ02 地图。管理员维护 `footprints.tencent` 的展示 Key、搜索解析 Key，以及 `footprints.baidu` 的查询 AK / H5 底图；密钥加密存入 `system_config`，不发给客户端。搜索采用百度优先，配额耗尽或并发受限时切换腾讯；不根据免费余额判断切换。

两个腾讯 Key 必须不同，搜索解析凭据需按服务商要求配置出口 IP 权限。GL 代理依赖本站登录和限定路径 Cookie；默认 `style1` 需在腾讯控制台绑定白浅模板。具体路由、权限、配额回退与配置语义见[腾讯地图代理接入](docs/腾讯地图代理接入.md)。模拟测试不代表真实地图权限、额度或定位精度验收。

省市区县乡镇目录由 `system_region` 提供，接口为 `/system/regions` 与 `/system/regions/{code}/path`，需登录但不需要地图 Key。当前为固定 2023 快照，不涵盖港澳台及海外；手填地址与选点仍可使用。来源、许可及更新方式见[地区数据](docs/data/china-regions.md)。

### Typesafe 与 MBTI

「系统管理 → 系统配置」维护 `typesafe.recommendation` 与 `mbti.api`，保存后即时生效。首次保存前读取 `AIO_LIFE_TYPESAFE_*` / `AIO_LIFE_MBTI_*` 初始值，此后以数据库为准；清除密钥不会重新回退环境变量。Typesafe 缺少密钥时使用日历规则，MBTI 缺少密钥时提示管理员配置。

密钥留空保留、填写替换、显式清除；接口只返回是否已配置。修改 MBTI 服务地址时须重新填写密钥或明确清除，避免向新地址发送原密钥。加密依赖稳定的 `AIO_LIFE_NOTIFICATION_ENCRYPTION_KEY`。Jev 推荐联调见[测试与接入说明](docs/Jev时迹分类测试用例.md)。

## 代码与文档结构

```text
src/main/java/top/aiolife/
├── sso/            # 登录、用户、API Key、文件与二级锁
├── record/         # 时迹、目标、待办、阅读、财务、同步等生活记录
├── system/         # 菜单、字典、系统配置、日志与对象存储管理
├── bankcard/       # 银行卡与卡面
├── capture/        # 浏览器采集
├── export/         # 个人数据导出
├── scheduling/     # 定时任务编排
├── wardrobe/       # 衣柜
├── membership/     # 会员平台与订阅
├── feedback/       # 用户反馈
├── relationship/   # 可选关系图谱
├── mcp/            # MCP 工具注册与认证
├── docs/           # OpenAPI 渐进查询
├── core/           # 公共组件
└── config/         # 全局配置
sql/                # 全量基线、增量、业务与菜单种子
scripts/            # 数据库验证与参考数据生成
docker/             # 可选基础设施 Compose 与监控配置
docs/               # 当前契约、接入说明与历史记录
```

业务模块按 API、Service、Mapper、POJO 分层；MySQL 业务访问统一使用 MyBatis-Plus / MyBatis Mapper。完整架构边界与测试要求见 [AGENTS.md](AGENTS.md)，专题入口见[后端文档目录](docs/README.md)。

## 授权

本仓库未附独立 `LICENSE` 文件，项目授权需向维护者确认；第三方代码、图标与其他资源遵循各自许可证。
