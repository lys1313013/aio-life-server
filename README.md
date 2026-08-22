# AIO Life Server - 人生管理系统

> 记录、统计、分析人生的所有数据

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.10-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

## 📖 项目简介

**AIO Life** 是一款 All-in-One 人生管理系统，致力于全方位记录、统计与分析生活数据。通过主动录入与第三方同步，实现人生痕迹的全面数字化，助您洞察规律，掌控生活节奏。

### 相关链接

- 🌐 **前端项目**：[aio-life-front](https://github.com/lys1313013/aio-life-front)
- 🔧 **后端项目**：[aio-life-server](https://github.com/lys1313013/aio-life-server)（本仓库）

## 🛠 技术栈

### 核心框架

- **Java 21** — 现代化 Java 特性
- **Spring Boot 3.3.10** — 快速开发框架
- **MyBatis Plus 3.5.11** — 增强版 MyBatis ORM
- **Druid 1.2.24** — 高性能数据库连接池

### 数据存储

- **MySQL 8.x** — 关系型数据库
- **Redis** — 分布式缓存、Session 存储
- **Caffeine** — 本地内存缓存（二级缓存）
- **Neo4j** — 图数据库（人际关系图谱，可选）
- **MinIO** — 对象存储服务

### 认证与安全

- **Sa-Token 1.40.0** — 轻量级认证框架（JWT 模式）

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
CREATE DATABASE `aio-life` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 执行初始化脚本
mysql -u root -p aio-life < sql/1_init_table/2026-03-11_create_table.sql
# ... 执行其他 SQL 脚本
mysql -u root -p aio-life < sql/2_ini_data/*.sql
```

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

### API 文档

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
