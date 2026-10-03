# AIO Life Server - 人生管理系统

> 记录、统计、分析人生的所有数据

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

## 📖 项目简介

**AIO Life** 是一款 All-in-One 人生管理系统，致力于全方位记录、统计与分析生活数据。通过主动录入与第三方同步，实现人生痕迹的全面数字化，助您洞察规律，掌控生活节奏。

### 相关链接

- 🌐 **前端项目**：[aio-life-front](https://github.com/lys1313013/aio-life-front)
- 🔧 **后端项目**：[aio-life-server](https://github.com/lys1313013/aio-life-server)（本仓库）
- 🤖 **AI 接口调用**：[OpenAPI 文档获取与调用指南](docs/AI接口调用指南.md)

## 🛠 技术栈

### 核心框架

- **Java 21** — 现代化 Java 特性
- **Spring Boot 3.5.16** — 快速开发框架
- **MyBatis Plus 3.5.11** — 增强版 MyBatis ORM
- **Druid 1.2.24** — 高性能数据库连接池

### 数据存储

- **MySQL 8.x** — 关系型数据库
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
- `DELETE /api/system/storage/object?key=...`：删除与上传按桶名/对象名共用互斥锁，在锁内实时查询 `file` 及 CBTI 图片引用；匹配完整对象名、历史带桶路径及旧文件名/属主/业务目录组合。有任意关联（包括 `biz_id` 为空、软删除记录）或对象正在处理均返回 HTTP 409；加锁/查库失败不会执行存储删除。上传锁保持到数据库事务完成，回滚清理早于释放锁。

上传和删除实例需同步升级以遵循同一对象锁协议；滚动升级存在旧实例时应暂停管理员清理。

所有入口都校验 `admin` 角色，包括预览、下载和删除。前端通过 Authorization 请求图片 Blob；不返回 MinIO 凭据、公开链接或签名链接，不修改现有桶访问策略。对象 key 按原始值传递，由请求客户端编码，保留中文、空格、`+`、`#` 和目录分隔符。文件大小沿用全局 Long 序列化约定，Web 兼容字符串数值。

已有数据库升级时执行 `sql/2_ini_data/2026-10-02_storage_admin_menu.sql`（可重复执行）。菜单配置直接读取数据库；执行后重新登录或刷新前端菜单以更新路由。MinIO 凭据需要业务桶的 ListBucket、GetObject 与 DeleteObject 权限。

### 认证与安全

- **Sa-Token 1.40.0** — 轻量级认证框架（JWT 模式）

#### 阅读与观影封面

自动导入封面只请求白名单 CDN 的 HTTPS 地址，端口仅允许默认端口或 443，拒绝 URL 用户信息和片段，不跟随任何重定向。主机名通过 URI 解析，允许白名单域名本身及其任意子域名，按点号边界匹配；例如 `img1.doubanio.com` 可用，`fakedoubanio.com` 和 `doubanio.com.evil.com` 不可用。

`AIO_LIFE_DOUBAN_COVER_ALLOWED_DOMAINS` 配置逗号分隔的域名，默认 `doubanio.com`，无需列举 `img1`、`img2` 等前缀。配置值不带前导点号或 `*.`；显式设为空值可禁用远程封面下载。配置一个域名即信任其全部子域名，仅应配置已核实的豆瓣图片域名，不添加任意第三方域名或 IP。

自定义封面通过图片上传接口获取 `fileId`，新增/编辑阅读及观影记录时提交该 ID；非白名单 `coverImgUrl` 且没有 `fileId` 的请求会被拒绝。豆瓣解析遇到被拒绝的封面来源时仍返回书影信息，但清空封面 URL，可手动上传封面。

本策略限制可信来源并阻止重定向绕过；尚未实现 DNS/IP 固定连接及网络出口隔离，不能视为完整的通用 URL 抓取防护。

### AI 能力

- **LangChain4j 1.12.2** — LLM 应用开发框架
- **MCP 0.14.0** — Model Context Protocol 支持

### 工具库

- **MapStruct 1.6.0** — 高性能对象映射
- **Hutool 5.8.36** — Java 工具类库
- **fastjson2 2.0.57** — JSON 处理
- **jsoup 1.17.2** — HTML 解析
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
│   ├── llm/              # LLM/AI 功能
│   ├── mcp/              # MCP 协议支持
│   ├── core/             # 公共组件：ApiResponse、异常处理、工具类
│   └── config/           # 全局配置
├── src/main/resources/
│   └── application.yml   # 应用配置
├── sql/
│   ├── 1_init_table/     # 建表脚本
│   └── 2_ini_data/       # 初始化数据
├── docker/               # Docker 配置文件
└── docs/                 # 文档
```

## 🚀 快速开始

数据库访问统一使用 MyBatis-Plus / MyBatis Mapper。生产业务代码禁止使用 `JdbcTemplate` 等直接 JDBC 访问方式，Service / Guard 不得内嵌 SQL；联表和行锁也必须放入 Mapper。完整规则见 [AGENTS.md：数据库访问规范](AGENTS.md#数据库访问规范强制)，由 `PersistenceArchitectureTest` 和真实数据库实体映射测试共同检查。

### 环境要求

- JDK 21+
- MySQL 8.x
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
mysql -u root -p
CREATE DATABASE `aio_life` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 执行初始化脚本
mysql -u root -p aio_life < sql/1_init_table/2026-08-18_init_all_tables.sql
# 全量结构已经包含历史 ALTER，不能再次逐个执行增量结构脚本
for sql_file in sql/2_ini_data/*.sql; do
  mysql -u root -p aio_life < "$sql_file"
done
```

已有数据库升级及发布前只读结构预检见 [数据库升级验证](docs/数据库升级验证.md)。CI 分别验证新库与固定历史版本的增量升级，二者成功才允许镜像发布。

#### 3. 配置环境变量

创建 `.env` 文件或设置环境变量：

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

```bash
# 启动依赖服务（MySQL、Redis）
docker-compose -f docker/docker-compose.yml up -d

# 启动 MinIO（可选）
docker-compose -f docker/docker-compose-minio.yml up -d

# 启动 Neo4j（可选）
docker-compose -f docker/docker-compose-neo4j.yml up -d

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
- 旧库执行 `2026-10-03_user_avatar_file_id.sql`：直接删除 `avatar` 列，不迁移旧头像，用户需重新上传。先备份，协调后端、Web 与移动端一起升级；旧客户端的 `avatar` 不再作为头像保存字段。
- `avatar_file_id` 外键阻止直接删除被引用的文件记录；存储管理仍禁止删除 file 表关联的对象。解除绑定不自动删除文件。回滚旧程序需要同时恢复旧结构备份，不能只切换镜像。

### API 文档

推荐渐进读取：`GET /api/docs/catalog` 查看模块，`GET /api/docs/operations?keyword=目标` 分页搜索，`GET /api/docs/operations/{operationId}` 获取单接口定义及必要 Schema。三个入口均无需登录或 API Key。

启动服务后访问各模块接口，统一返回格式：

```json
{
  "rscode": "0",
  "result": null,
  "data": {...}
}
```

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
- ✅ **AI 助手** — LLM 集成，智能分析
- ✅ **MCP 协议** — Model Context Protocol 支持

## 📊 监控与运维

- **Prometheus 指标**：`http://localhost:45679/actuator/prometheus`
- **健康检查**：`http://localhost:45679/actuator/health`
- **链路追踪**：日志中包含 `traceId` 和 `spanId`

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

[MIT License](LICENSE)

## 📮 联系方式

如有问题或建议，请通过 GitHub Issues 反馈。
