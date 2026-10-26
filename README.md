# AIO Life Server - 人生管理系统

> 记录、统计、分析人生的所有数据

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-brightgreen.svg)](https://spring.io/projects/spring-boot)

## 📖 项目简介

**AIO Life** 是一款 All-in-One 人生管理系统，致力于全方位记录、统计与分析生活数据。通过主动录入与第三方同步，实现人生痕迹的全面数字化，助您洞察规律，掌控生活节奏。

### 相关链接

- 🌐 **前端项目**：[aio-life-front](https://github.com/lys1313013/aio-life-front)
- 🔧 **后端项目**：[aio-life-server](https://github.com/lys1313013/aio-life-server)（本仓库）
- 📚 **文档导航**：[后端文档目录](docs/README.md)
- 🤖 **AI 接口调用**：[OpenAPI 文档获取与调用指南](docs/AI接口调用指南.md)

## 🛠 技术栈

### 核心框架

- **Java 21** — 现代化 Java 特性
- **Spring Boot 3.5.16** — 快速开发框架
- **MyBatis Plus 3.5.11** — 增强版 MyBatis ORM
- **Druid 1.2.24** — 高性能数据库连接池

### 数据存储

- **MySQL** — 关系型数据库；117 库实际为 5.7.44（2026-10-22 核验），CI 使用 8.0
- **Redis** — 分布式缓存、Session 存储
- 菜单与二级锁配置直接通过 Mapper 读取数据库权威状态，避免数据库提交后 Redis 缓存失效失败留下旧授权配置；`/menu/all` 仍按当前角色过滤。代价是增加小配置读取，不缓存跨请求的授权配置。密码解锁状态仍使用 Redis，时长为 30 分钟。
- **Caffeine** — 本地内存缓存（二级缓存）
- **Neo4j** — 图数据库（人际关系图谱，可选）
- **MinIO** — 对象存储服务

#### 管理员对象存储浏览

Web 入口为「系统管理 → 对象存储」。读取 `AIO_LIFE_MINIO_BUCKET_NAME` 配置的业务桶，支持目录、路径前缀筛选、游标分页、图片预览及下载，包含未登记到业务 `file` 表的对象。每页默认 24 条，最多 100 条，不扫描全桶统计总数。支持删除没有 file 表关联的单个对象，不提供上传、目录批量删除或桶策略修改。

- `GET /api/system/storage/objects`：`prefix`、`cursor`、`pageSize`；`nextCursor` 为空表示末页，切换前缀后重置游标。
- `GET /api/system/storage/preview?key=...`：预览不超过 20 MB 的 JPG、PNG、GIF、WebP、AVIF、BMP 图片。
- `GET /api/system/storage/download?key=...`：以附件方式下载，其他格式也可使用。
- `DELETE /api/system/storage/object?key=...`：删除与上传按桶名/对象名共用互斥锁，在锁内实时查询 `file` 及 CBTI 图片引用；按当前完整对象键精确匹配。有任意关联（包括 `biz_id` 为空、软删除记录）或对象正在处理均返回 HTTP 409；加锁/查库失败不会执行存储删除。上传锁保持到数据库事务完成，回滚清理早于释放锁。

上传和删除共用同一对象锁协议。

所有入口都校验 `admin` 角色，包括预览、下载和删除。前端通过 Authorization 请求图片 Blob；不返回 MinIO 凭据、公开链接或签名链接，不修改现有桶访问策略。对象 key 按原始值传递，由请求客户端编码，保留中文、空格、`+`、`#` 和目录分隔符。文件大小沿用全局 Long 序列化约定，Web 兼容字符串数值。

菜单初始化执行 `sql/menu/2_ini_data/2026-10-02_storage_admin_menu.sql`（可重复执行）。菜单配置直接读取数据库；执行后重新登录或刷新前端菜单以更新路由。MinIO 凭据需要业务桶的 ListBucket、GetObject 与 DeleteObject 权限。

### 认证与安全

- **Sa-Token 1.40.0** — 轻量级认证框架（JWT 模式）

#### 阅读与观影封面

自动导入封面只请求白名单 CDN 的 HTTPS 地址，端口仅允许默认端口或 443，拒绝 URL 用户信息和片段，不跟随任何重定向。主机名通过 URI 解析，允许白名单域名本身及其任意子域名，按点号边界匹配；例如 `img1.doubanio.com` 可用，`fakedoubanio.com` 和 `doubanio.com.evil.com` 不可用。

`AIO_LIFE_DOUBAN_COVER_ALLOWED_DOMAINS` 配置逗号分隔的域名，默认 `doubanio.com`，无需列举 `img1`、`img2` 等前缀。配置值不带前导点号或 `*.`；显式设为空值可禁用远程封面下载。配置一个域名即信任其全部子域名，仅应配置已核实的豆瓣图片域名，不添加任意第三方域名或 IP。

自定义封面通过图片上传接口获取 `fileId`，新增/编辑阅读及观影记录时提交该 ID；非白名单 `coverImgUrl` 且没有 `fileId` 的请求会被拒绝。豆瓣解析遇到被拒绝的封面来源时仍返回书影信息，但清空封面 URL，可手动上传封面。

本策略限制可信来源并阻止重定向绕过；尚未实现 DNS/IP 固定连接及网络出口隔离，不能视为完整的通用 URL 抓取防护。

### MCP 工具能力

- **LangChain4j 1.12.2** — MCP 工具注解与 Schema 适配
- **MCP 0.14.0** — Model Context Protocol 支持

服务端已移除 LLM 会话、消息和模型密钥 API 与业务代码。对应的 `llm_key`、`conversation`、`chat_message` 表及实体 Mapper 已从当前初始化和源码移除。

### 工具库

- **MapStruct 1.6.0** — 高性能对象映射
- **Hutool 5.8.36** — Java 工具类库
- **fastjson2 2.0.66** — JSON 处理
- **jsoup 1.23.2** — HTML 解析
- **Lombok 1.18.38** — 简化代码

### 监控与运维

- **Micrometer + Brave** — 可观测性（Prometheus 指标 + 分布式链路追踪）
- **Log4j2** — 日志框架

### 构建工具

- **Maven** — 项目构建与依赖管理

## 📁 项目结构

```
aio-life-server/
├── src/main/java/top/aiolife/
│   ├── sso/              # 认证模块：登录、注册、验证码、API Key
│   ├── record/           # 核心记录引擎：时迹、目标、待办、理财、荣誉等
│   ├── system/           # 系统管理：用户、菜单、字典
│   ├── wardrobe/         # 衣柜管理
│   ├── relationship/     # 人际关系图谱（Neo4j，可选）
│   ├── mcp/              # MCP 协议支持
│   ├── core/             # 公共组件：ApiResponse、异常处理、工具类
│   └── config/           # 全局配置
├── src/main/resources/
│   └── application.yml   # 应用配置
├── sql/
│   ├── 1_init_table/     # 建表脚本
│   ├── 2_ini_data/       # 业务初始化数据
│   └── menu/             # 菜单初始化数据
├── docker/               # Docker 配置文件
└── docs/                 # 文档
```

## 🚀 快速开始

数据库访问统一使用 MyBatis-Plus / MyBatis Mapper。生产业务代码禁止使用 `JdbcTemplate` 等直接 JDBC 访问方式，Service / Guard 不得内嵌 SQL；联表和行锁也必须放入 Mapper。完整规则见 [AGENTS.md：数据库访问规范](AGENTS.md#数据库访问规范强制)，由 `PersistenceArchitectureTest` 和真实数据库实体映射测试共同检查。

### 环境要求

- JDK 21+
- MySQL：117 现有库为 5.7.44；新库初始化与 CI 按 8.0.16+ 验证，现有库迁移须逐份核验版本兼容性，见 [SQL 脚本规范](docs/SQL脚本规范.md)
- Redis
- MinIO（可选，用于文件存储）
- Maven 3.6+

### 安装步骤

#### 1. 克隆项目

```bash
git clone https://github.com/lys1313013/aio-life-server.git
cd aio-life-server
```

#### 2. 配置数据库

```bash
# 创建 MySQL 数据库
mysql -u root -p -e 'CREATE DATABASE `aio_life` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'

# 执行初始化脚本
mysql -u root -p aio_life < sql/1_init_table/2026-08-18_init_all_tables.sql
# 只初始化当前完整结构
mysql -u root -p aio_life < sql/menu/2_ini_data/2_menu.sql
for sql_file in sql/2_ini_data/*.sql sql/menu/2_ini_data/202*.sql; do
  mysql -u root -p aio_life < "$sql_file"
done
```

应用数据源用户名固定为 `aio_life`（见 `application.yml`）；初始化后为该账号授予业务库权限，并通过 `AIO_LIFE_DB_PASSWORD` 配置对应密码。上述 `root` 仅用于初始化，不能把 root 密码直接当作应用账号密码。

当前空库初始化、隔离验证及只读结构预检见 [数据库初始化与验证](docs/数据库初始化与验证.md)。CI 验证当前全量结构和种子数据，不维护历史基线升级。

#### 3. 配置环境变量

Maven／`java -jar` 启动前，在当前终端导出环境变量。应用和 `start.sh` 不会自动读取 `.env`；Docker 的 `--env-file` 读取不含 `export` 的 `KEY=value` 文件。

```bash
# 数据库配置
export AIO_LIFE_DB_PASSWORD=your_db_password
export AIO_LIFE_DB_URL=127.0.0.1:3306

# Redis 配置
export AIO_LIFE_REDIS_HOST=127.0.0.1
export AIO_LIFE_REDIS_PORT=6379
export AIO_LIFE_REDIS_PASSWORD=your_redis_password

# MinIO 配置（可选）
export AIO_LIFE_MINIO_ENDPOINT=http://localhost:1300
export AIO_LIFE_MINIO_ACCESS_KEY=your_access_key
export AIO_LIFE_MINIO_SECRET_KEY=your_secret_key
export AIO_LIFE_MINIO_BUCKET_NAME=aiolife

# Neo4j 配置（可选，默认关闭）
export AIO_LIFE_NEO4J_ENABLED=false
export AIO_LIFE_NEO4J_URI=bolt://localhost:7687
export AIO_LIFE_NEO4J_USERNAME=neo4j
export AIO_LIFE_NEO4J_PASSWORD=your_neo4j_password

# 邮件配置（可选）
export AIO_LIFE_MAIL_USERNAME=your_email@qq.com
export AIO_LIFE_MAIL_PASSWORD=your_email_password
```

#### 4. 启动服务

```bash
# 方式一：Maven 直接运行
mvn spring-boot:run

# 方式二：先编译再运行
mvn clean package -DskipTests
java -jar target/aio-life-server-1.0.0.jar
```

服务启动后访问：`http://localhost:45678/api`

#### 快速启动 / 快速测试脚本

```bash
# 一键启动：校验 MySQL/Redis 连通后直接用现有配置运行服务（不拉起任何容器）
./start.sh
./start.sh --clean                 # mvn clean 后启动

# 快速测试：默认跑全部测试，也可指定测试类/方法
./test.sh
./test.sh TimeTrackerCategoryControllerIntegrationTest
./test.sh TimeTrackerCategoryControllerIntegrationTest#testList_获取分类列表
```

`start.sh` 可选环境变量：`SKIP_DEPS_CHECK=true`（跳过 MySQL/Redis/Neo4j 校验）、`AIO_LIFE_NEO4J_ENABLED=true`（开启 Neo4j 连通校验，详见脚本头部注释）。

### 使用 Docker 部署

先准备可达的 MySQL、Redis、业务账号及数据库结构。容器内的 `localhost` 指容器自身，`.env` 中的依赖地址须改为容器可达地址；下面的可选 Compose 文件不负责创建业务库。示例仅发布应用端口 45678；监控如需访问 45679，另行配置受限网络及端口映射。

```bash
# 可选监控（Prometheus、Grafana）；此文件不提供 MySQL 或 Redis
docker compose -f docker/docker-compose.yml up -d

# 启动 MinIO（可选）
docker compose -f docker/docker-compose-minio.yml up -d

# 启动 Neo4j（可选）
docker compose -f docker/docker-compose-neo4j.yml up -d

# 构建应用镜像
docker build -t aio-life-server:latest .

# 运行应用
docker run -d \
  -p 45678:45678 \
  --env-file .env \
  aio-life-server:latest
```

## 🔧 开发指南

### 常用命令

```bash
# 运行测试
mvn test

# 运行单个测试类
mvn test -Dtest=TimeTrackerCategoryControllerIntegrationTest

# 运行单个测试方法
mvn test -Dtest=TimeTrackerCategoryControllerIntegrationTest#testList_获取分类列表

# 编译（跳过测试）
mvn clean package -DskipTests
```

### 端口说明

| 服务 | 端口 | 说明 |
|---|---|---|
| 主应用 | 45678 | API 服务，context-path: `/api` |
| 管理端点 | 45679 | Prometheus、Health、Info |

### 头像文件绑定与公网部署

- 用户表只保存 `avatar_file_id`（关联 `file.id`），不保存头像 URL。更新资料提交 `avatarFileId`：不传保持原值，传 `null` 清除，非空必须是当前用户上传、未删除且公开的头像图片。
- `/user/info`、`/auth/info` 和 `/user/{id}/basic` 返回 `avatarFileId`、`avatarUrl`；展示地址为当前 `AIO_LIFE_SERVER_BASE_URL` + `/file/preview/{fileId}`。部署变量必须使用 `SERVER` 拼写并配置公网 HTTPS，如 `https://aiolife.top/api`。
- 当前用户头像只存 `avatar_file_id`，重新上传通过统一文件入口绑定。
- `avatar_file_id` 外键阻止直接删除被引用的文件记录；存储管理仍禁止删除 file 表关联的对象。解除绑定不自动删除文件。开发环境按当前结构与当前程序配套运行。

### API 文档

推荐渐进读取：`GET /api/docs/catalog` 查看模块，`GET /api/docs/operations?keyword=目标` 分页搜索，`GET /api/docs/operations/{operationId}` 获取单接口定义及必要 Schema。三个入口均无需登录或 API Key。

启动服务后访问各模块接口，统一返回格式：

```json
{
  "code": 0,
  "message": null,
  "data": null
}
```

`code` 为整数，`0` 表示业务成功；`message` 为可空提示信息，`data` 保留接口原有的数据类型，无数据时为 `null`。HTTP 状态码行为保持不变。

该格式替换旧的 `rscode / result / data` 协议，Web、移动端及其他 API 调用方须与后端同步升级，旧客户端不兼容新响应。


## 📝 功能模块

- ✅ **时间追踪** — 记录时间花费，分类统计
- ✅ **目标管理** — 设定目标，跟踪进度
- ✅ **待办事项** — 任务管理，优先级排序
- ✅ **理财记录** — 收支统计，财务分析
- ✅ **荣誉成就** — 记录个人成就与荣誉
- ✅ **备忘录** — 快速记录想法与灵感
- ✅ **第三方同步** — LeetCode、CSDN、GitHub 数据同步
- ✅ **衣柜管理** — 衣物分类管理
- ✅ **人际关系** — 人脉图谱（Neo4j，需启用）
- ✅ **MCP 协议** — Model Context Protocol 支持

## 📊 监控与运维

- **Prometheus 指标**：`http://localhost:45679/actuator/prometheus`
- **健康检查**：`http://localhost:45679/actuator/health`
- **链路追踪**：日志中包含 `traceId` 和 `spanId`

## 微信小程序订阅消息

一期支持力扣未打卡提醒，渠道默认关闭。复用小程序身份、Token 缓存和通知投递表；配置模板 ID 和实际字段名后，可在小程序「我 → 通知设置」订阅一次并发送测试。管理员在「系统配置 → 微信小程序与订阅通知」集中填写并保存，立即生效；AppSecret 加密存入现有 `system_config`，不回显。部署侧保留通知加密主密钥和基础设施配置。配置、API、投递状态和验收流程见 [微信小程序订阅消息接入](../docs/微信小程序订阅消息接入.md)。独立 checkout 可查看 [主仓库文档](https://github.com/lys1313013/aio-life/blob/main/docs/微信小程序订阅消息接入.md)。

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

本仓库当前未附独立 `LICENSE` 文件，项目授权需向维护者确认；第三方代码、图标与其他资源遵循各自许可证。

## 📮 联系方式

如有问题或建议，请通过 GitHub Issues 反馈。

### 已登录 App 扫码登录 Web

Web 登录页的“App 扫码登录”入口为 `/auth/app-qrcode-login`。用户在已登录的 AIO Life App / 微信小程序中进入“我 → 扫码登录”，扫描二维码并核对四位数字后确认。该入口使用自有登录会话，与“微信扫一扫打开小程序”的微信登录入口独立，不要求微信开放平台配置。

接口前缀 `/api/auth/qr-login`：`POST /` 创建，`GET /{id}/status` 查询，`POST /scan` 扫码，`POST /decision` 确认/拒绝，`POST /consume` 兑换，`POST /cancel` 撤销。查询凭证通过 `X-QR-Secret` 请求头传递；兑换和撤销使用请求体 `{id,browserSecret}`。App 仅提交 `{id,ticket}` / `{id,ticket,approve}`，身份来自 Bearer 登录会话，API Key 和仅 Cookie 鉴权不能授权扫码登录。

二维码有效期 120 秒，浏览器凭证与扫码票据独立生成。Redis 原子状态转换防止并发接管和重复签发；兑换完成后保留 30 秒相同结果，供原浏览器在响应丢失时重取。签发过程中失败或进程退出时不重新开放该票据，需刷新二维码。兑换前校验授权会话及账号状态，新 Web Token 独立于 App Token，遵循现有二级锁策略。浏览器专属凭证不写 URL、二维码、日志或浏览器持久化存储；正式部署使用 HTTPS。

不需要数据库迁移。发布时先更新后端，再更新 Web 和移动端；Redis 须可用。反向代理保留 `Authorization`、`X-QR-Secret` 请求头，对本组接口不缓存。App 需要重新打包以包含扫码原生模块，H5 不提供摄像头扫码入口。

专项验证：`mvn -Dspring.profiles.active=test -Dtest=QrLoginHttpIntegrationTest test`（需要测试 MySQL / Redis，测试账号事务回滚）。

## 生活足迹

入口 `/record/life-footprints`，记录长期生活的起止日期、具体地点、地址和备注。数据库使用 `DATE`，接口使用 `YYYY-MM-DD`；结束日期必填，`9999-12-31` 表示至今，不使用 `ongoing`。地图取得当前用户全量记录。API 前缀 `/api/life-footprints`，增改使用完整字段，不使用幂等键或版本字段，暂不使用乐观锁；同一账号多端编辑以最后一次成功保存为准。`update_time` 仅用于审计，修改和删除仍校验用户归属及逻辑删除状态。保存期间拦截重复点击；网络结果不确定时先重新读取确认，不承诺重复新增请求返回同一记录。

部署前按 [数据库初始化说明](docs/数据库初始化与验证.md) 完成当前结构和菜单初始化。Web 主地图与新增、编辑弹窗统一使用腾讯 JavaScript API GL；默认 `style1` 须在腾讯控制台绑定白浅模板。地图 Key 通过官方代理方案由后端注入，SDK 加载 URL 不含 Key。

管理员在「系统管理 → 系统配置 → 生活足迹腾讯地图（Web）」维护两个不同的 Key，保存在现有 `system_config` 表的 `footprints.tencent` JSON 项，无需新增表。配置实时生效；页面刷新后更新地图可用状态。完整时序、路由和权限设置见[腾讯地图代理接入](docs/腾讯地图代理接入.md)。

| 配置 | 用途 |
| --- | --- |
| 地图展示 Key（`browserKey`） | 只用于 GL 校验、统计和海外控制；加密为 `browserKeyCiphertext`，不启用 WebService |
| 搜索解析 Key（`serviceKey`） | 腾讯关键词输入提示、地址解析、逆地址解析；加密为 `serviceKeyCiphertext`，WebService 设置后端公网出口 IP 白名单 |
| 样式（`mapStyleId`） | 默认 `style1` |
| 搜索超时（`timeoutMs`） | 1000–15000ms，默认 5000ms，不影响 GL 代理的 8 秒超时 |

两个 Key 分别留空保留、填写替换、显式清除，不允许同值。管理响应仅返回 `browserKeyConfigured`、`serviceKeyConfigured`、样式与超时，不回显明文或密文。加密主密钥为部署侧 `AIO_LIFE_NOTIFICATION_ENCRYPTION_KEY`（至少 16 位，保持稳定）。展示 Key 加密保存；腾讯搜索解析 Key 必须单独配置，不借用展示 Key。后台查询优先使用 `footprints.baidu` 的服务端 AK；百度配额耗尽或并发受限时改用腾讯搜索解析 Key。应用不能代替腾讯控制台设置权限；授权粒度及是否能单独禁用 JS API 以厂商控制台为准，当前服务端不计算 SN 签名。

`GET /api/life-footprints/map/config` 返回 `searchEnabled`、`coordinateSystem: "GCJ02"`、兼容移动端的 `tiles` 和 `tencent: { enabled, mapStyleId }`，不含凭据。GL 请求使用限定代理路径的加密 HttpOnly Cookie，仍须通过本站登录、账号状态与二级锁；搜索解析使用普通 Authorization。Key 只在后端调用固定腾讯上游时注入，本站凭据不转发给腾讯。百度与腾讯任一查询凭据已配置就启用搜索；两家均未配置时仍可展示地图并手工选点、填写地址。

后台通过 `FootprintMapProvider` 接口及百度、腾讯两个实现统一处理搜索、地理编码与逆解析。默认百度优先；百度业务状态 `4/301/302`、并发状态 `401/402` 或 HTTP `429` 触发同一次请求切换腾讯。额度耗尽按 AK 指纹与 API 路径记入 Redis，后续命中直接跳过百度：日额度至北京时间次日零点、永久配额缓存 24 小时后重新探测。并发限制只切换当次，不写额度状态；Redis 不可读时也直接走腾讯。普通错误、超时和空结果不切换。每家最多调用一次，更换 AK 使用独立状态。返回统一 GCJ02，逆解析不改变用户选点，候选定位仍须确认。该逻辑依据上游配额错误，不读取免费余额；若厂商继续消耗付费流量包并返回成功，无法检测免费额度已用完。

「生活足迹百度查询与 H5 底图」配置（`footprints.baidu`）维护优先使用的 `serviceAk`（仅写入，加密存为 `serviceAkCiphertext`）与 `timeoutMs`，留空保留、填写替换、`clearServiceAk` 显式清除；按当前配置保存。百度控制台应开通地点检索、地理编码与逆地理编码，并使用后端公网出口 IP 白名单。移动端 H5 保持 Leaflet，该表单同时维护其底图地址、坐标系与署名，默认 OpenStreetMap，遵循[瓦片使用政策](https://operations.osmfoundation.org/policies/tiles/)。Web 不使用该底图配置。微信和 App 保持原生 GCJ02 地图；移动端共用后端查询接口，因此同样采用百度优先、配额耗尽或并发受限后腾讯备用。

本地专项：`mvn -Dtest=BaiduMapQuotaStoreTest,FootprintMapClientTest,BaiduFootprintMapProviderTest,TencentFootprintMapProviderTest,FootprintMapConfigServiceTest,FootprintTencentConfigServiceTest,TencentMapProxyClientTest,TencentMapProxyHttpTest,TencentMapProxySessionFilterTest test`。真实腾讯搜索解析须单独配置有效服务端 Key 后联调；模拟测试不代表厂商权限或定位精度验收。

#### 省市区县乡镇目录

生活足迹 Web / H5 / 微信共享 `system_region` 基础表，按需调用：

- `GET /api/system/regions?parentCode=0`：根省份；传父编码加载其下级，返回 `{code,name,level,hasChildren}` 数组，`level` 为数字1至4、`code` 为字符串。
- `GET /api/system/regions/{code}/path`：恢复从省到所选编码的完整路径。均需登录，无需地图AK。
- 足迹请求/响应新增可空 `regionCode`，保存最深选中编码；可停在任一级或不选，未填写时为 NULL。变更地区后需重新确认具体地图位置。

使用当前全量结构并初始化地区目录种子数据。目录使用固定2023快照，共44703项，不含港澳台/海外，不保证区划实时性；手填地址与地图选点仍可使用。来源、许可、覆盖范围及再生成方式见 [地区数据](docs/data/china-regions.md)。


### Typesafe 与 MBTI 系统配置

管理员在「系统管理 → 系统配置」维护以下两项，无需手动建表或插入记录；复用现有 `system_config`，首次保存自动新增，后续更新同一行。

| 配置键 | 内容 | 校验范围 |
| --- | --- | --- |
| `typesafe.recommendation` | 智能推荐开关、API Key、模型、请求超时、数据缓存时长 | 超时 1–60000ms；缓存 0–604800000ms，0 关闭缓存；模型名称最多 128 字符 |
| `mbti.api` | API Key、接口地址 | 地址必须为 HTTPS，不含账号、查询参数或片段 |

首次保存前分别使用 `AIO_LIFE_TYPESAFE_*`、`AIO_LIFE_MBTI_*` 的兼容值；首次保存会将当前有效参数及加密后的密钥一并存入数据库，此后以数据库为准。明确清除密钥后不会重新回退到环境变量。修改后无需重启，下一次业务请求使用新配置；已发出的请求完成本次调用时仍使用原快照。Typesafe 的连接／读取／总等待超时同步调整，数据缓存时长变化后各实例在下一次读取时切换缓存，旧请求不能回填新缓存。

密钥沿用微信配置的加密服务与 `AIO_LIFE_NOTIFICATION_ENCRYPTION_KEY`：留空保留、填写替换，管理接口仅返回 `apiKeyConfigured`，不返回明文或密文。还可明确勾选清除；Typesafe 缺少密钥时回退日历规则，MBTI 缺少密钥时提示管理员配置。修改 MBTI 接口地址时须重新填写密钥或明确清除，避免把原密钥自动发送到新地址。部署侧加密主密钥应保持稳定。

验证码限流统一读取 `aio.life.server.auth.code.*`，默认发送间隔 180 秒、单 IP 每日 100 次、单邮箱每日 20 次、全局每日 1000 次。原先误写的 `spring.auth.code.*` 读取路径已修正；如部署曾额外覆盖旧路径，应同步改用正式路径。本次限流配置仍由启动配置读取，不属于后台动态配置。
