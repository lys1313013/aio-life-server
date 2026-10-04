# AGENTS.md

This file provides guidance to AI coding agents when working with code in this repository.

## 项目概述

**AIO Life Server** — All-in-One 人生管理系统的后端服务，记录、统计、分析人生的所有数据。

## 技术栈

- **Java 21** + **Spring Boot 3.5.16**
- **MyBatis Plus 3.5.11** + **Druid 1.2.24** (ORM + 连接池)
- **MySQL 8.x** (主数据库)
- **Redis** (分布式缓存、Session、Sa-Token 存储)
- **Caffeine** (本地内存缓存，配合 Spring Cache 作为二级缓存)
- **Sa-Token 1.40.0** (认证授权，JWT 模式)
- **MinIO** (对象存储)
- **Neo4j** (关系图谱，可选模块)
- **LangChain4j 1.12.2** + **MCP 0.14.0** (MCP 工具注册与 Schema 适配)
- **MapStruct 1.6.0** (对象映射)
- **Hutool 5.8.36** (Java 工具库)
- **fastjson2 2.0.57** (JSON 处理)
- **jsoup 1.17.2** (HTML 解析，用于第三方数据同步)
- **Lombok 1.18.38**
- **Log4j2** (日志框架)
- **Micrometer + Brave** (可观测性：Prometheus 指标 + 分布式链路追踪)
- **Maven** (构建工具)

## 常用命令

```bash
# 启动服务（端口 45678）
mvn spring-boot:run

# 编译（跳过测试）
mvn clean package -DskipTests

# 运行测试
mvn test

# 运行单个测试类
mvn test -Dtest=TimeTrackerCategoryControllerIntegrationTest

# 运行单个测试方法
mvn test -Dtest=TimeTrackerCategoryControllerIntegrationTest#testList_获取分类列表
```

## 端口与端点

| 服务 | 端口 | 说明 |
|---|---|---|
| 主应用 | 45678 | API 服务，context-path: `/api` |
| 管理端点 | 45679 | Prometheus、Health、Info |

## 模块架构

源码位于 `src/main/java/top/aiolife/`，按业务领域垂直拆分：

| 模块 | 职责 |
|---|---|
| `sso` | 登录认证：Sa-Token JWT + Redis，邮件验证码，用户绑定，API Key 管理 |
| `record` | 核心记录引擎：时迹、目标、待办、理财、荣誉、备忘、消息通知、第三方同步（LeetCode/CSDN/GitHub）、仪表盘 |
| `system` | 系统管理：用户、菜单、字典 |
| `wardrobe` | 衣柜管理 |
| `relationship` | 人际关系图谱（Neo4j），通过 `AIO_LIFE_NEO4J_ENABLED` 环境变量控制 |
| `llm` | 历史会话、消息及模型密钥配置管理；不提供大模型调用 |
| `mcp` | MCP 协议支持：自定义注解驱动的 Tool 注册，含认证层 |
| `core` | 公共组件：ApiResponse、异常处理、工具类、常量 |
| `config` | 全局配置：Cache、CORS、JSON、MinIO、MyBatis Plus、Neo4j、Redis、Sa-Token |

### 模块内部分层

每个业务模块遵循标准分层：

```
<module>/
├── api/              # REST Controller
├── service/          # 业务逻辑接口
│   └── impl/         # 业务逻辑实现
├── mapper/           # MyBatis Plus Mapper
├── pojo/
│   ├── entity/       # 数据库实体
│   ├── vo/           # 视图对象（返回给前端）
│   ├── req/          # 请求对象
│   ├── dto/          # 数据传输对象
│   └── query/        # 查询对象
├── convertor/        # MapStruct 转换器
├── enums/            # 枚举
├── config/           # 模块级配置
├── aop/              # 切面
├── client/           # 外部服务客户端
├── autotask/         # 定时任务
└── mcp/              # MCP Tool（如果支持）
```

## 关键约定

### Java 类名与导入

- 普通 Java 代码（含测试）优先使用显式 `import` 和简单类名，避免在字段、方法签名、注解及方法体中重复书写全限定类名。
- 遇到同名类、内部类型遮蔽等冲突时，保留必要的全限定类名；不要为了缩短引用改变类型绑定。
- SpEL、AOP 切点、反射类名、MapStruct `expression` 等字符串中的类名按框架要求处理，不能直接依赖当前文件的 Java `import`，也不能批量替换。

### 数据库

- **逻辑删除**：全局配置 `is_deleted` 字段（0=未删除，1=已删除）
- **ID 生成**：使用雪花算法 `IdType.ASSIGN_ID`
- **基础实体**：所有业务实体继承 `BaseEntity`，包含 `id`、`createUser`、`createTime`、`updateUser`、`updateTime`、`isDeleted` 字段

### 数据库访问规范（强制）

- MySQL 业务数据访问统一使用 **MyBatis-Plus / MyBatis Mapper**。禁止在生产业务代码中使用 `JdbcTemplate`、`NamedParameterJdbcTemplate`、`JdbcClient` 或直接 JDBC 连接执行 SQL；不能因联表、行锁、性能或编写方便另起一套访问方式。
- 普通单表增删改查优先使用 `BaseMapper<Entity>` 和类型安全的 Wrapper。联表、`FOR UPDATE`、特殊审计更新等 SQL 放在 `mapper/` 的注解方法或对应 Mapper XML 中，参数使用 `#{...}` 绑定；禁止将用户输入拼接为 SQL。
- Controller、Service、Guard 只负责接口、业务规则和事务编排，不允许内嵌 SQL。需要读取软删除记录时，在 Mapper 中显式编写 SQL 并说明原因，不能改用 JDBC 绕开逻辑删除。
- 新增业务表必须同时提供准确的 `@TableName` 实体与 `BaseMapper`，通过 `PhysicalTableEntitySelectIntegrationTest` 的真实 MySQL 全字段查询。禁止添加 JDBC 表清单或跳过表来绕过检查。
- 迁移数据库访问方式必须保留用户归属、逻辑删除、审计字段、锁顺序和事务边界。需要清空的 nullable 字段使用显式 `set(..., null)` 或字段级 `FieldStrategy.ALWAYS`；不得全局启用空值覆盖。
- `FOR UPDATE` 等要求重新访问数据库的查询必须禁用查询缓存，并刷新一级缓存（如 `@Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)`），避免锁查询被缓存结果替代。
- 测试夹具、数据库结构检查可以使用 JDBC；数据源和事务管理配置不属于业务查询。`PersistenceArchitectureTest` 自动检查生产源码边界，不允许为业务代码添加豁免。

### API 响应格式

```java
// 统一返回 ApiResponse<T>
{
  "rscode": "0",      // 成功码为 "0"
  "result": null,     // 错误提示信息
  "data": {...}       // 业务数据
}
```

### 认证

- **Sa-Token JWT**：Token 通过 `Authorization: Bearer <token>` 传递
- **获取当前用户**：`StpUtil.getLoginIdAsLong()`
- **登录拦截**：默认拦截所有请求，白名单在 `SaTokenConfig` 配置

### MCP Tool 开发

```java
@McpToolProvider  // 标记为 MCP 工具提供者
@RequiredArgsConstructor
public class MyMcpTools {

    @Tool("工具描述，说明功能")
    public MyVO myTool(@Param("参数描述") String param) {
        // 实现逻辑
    }
}
```

### 对象映射

使用 MapStruct，Convertor 接口约定：

```java
@Mapper(builder = @Builder(disableBuilder = true))
public interface MyConvertor {
    MyConvertor INSTANCE = Mappers.getMapper(MyConvertor.class);

    MyVO entity2VO(MyEntity entity);
}
```

### RESTful 风格

- 接口尽量采用 RESTful 风格
- 注释使用标准 JavaDoc 形式
- 异常不主动捕捉，交由全局 `@RestControllerAdvice` 处理

## 环境变量

敏感配置通过环境变量注入，命名规范 `AIO_LIFE_*`：

| 变量 | 说明 | 默认值 |
|---|---|---|
| `AIO_LIFE_JWT_SECRET` | JWT 签名密钥（Sa-Token），生产必须覆盖 | aio_life（仅本地开发） |
| `AIO_LIFE_DB_PASSWORD` | 数据库密码 | root |
| `AIO_LIFE_DB_URL` | 数据库地址 | 127.0.0.1:3306 |
| `AIO_LIFE_REDIS_HOST` | Redis 地址 | 127.0.0.1 |
| `AIO_LIFE_REDIS_PORT` | Redis 端口 | 6379 |
| `AIO_LIFE_REDIS_PASSWORD` | Redis 密码 | 空 |
| `AIO_LIFE_MINIO_ENDPOINT` | MinIO 地址 | http://localhost:1300 |
| `AIO_LIFE_MINIO_ACCESS_KEY` | MinIO AK | aio_life |
| `AIO_LIFE_MINIO_SECRET_KEY` | MinIO SK | aio_life |
| `AIO_LIFE_MINIO_BUCKET_NAME` | MinIO Bucket | aiolife |
| `AIO_LIFE_NEO4J_ENABLED` | Neo4j 开关 | true |
| `AIO_LIFE_NEO4J_URI` | Neo4j URI | bolt://localhost:7687 |
| `AIO_LIFE_MAIL_USERNAME` | 邮件账号 | test@qq.com |
| `AIO_LIFE_MAIL_PASSWORD` | 邮件密码 | password |

## 测试

### 集成测试基类

继承 `BaseIntegrationTest`，自动 Mock Sa-Token：

```java
@SpringBootTest
@Transactional
class MyControllerTest extends BaseIntegrationTest {

    @Test
    void testMyMethod() {
        // 默认用户 ID 为 1L
        var response = myController.myMethod();
        assertSuccess(response);
    }
}
```

### 测试命名

使用中文描述测试场景：`testMethod_测试场景描述()`

## 数据库初始化

SQL 脚本位于 `sql/` 目录：

- `1_init_table/` — 建表脚本（按日期命名）
- `2_ini_data/` — 初始化数据脚本

**新库**：只执行 `1_init_table/2026-08-18_init_all_tables.sql`，再按序执行 `2_ini_data` 的数据脚本；全量结构已包含历史 ALTER，禁止再把所有增量结构脚本重复执行。

**旧库**：按 `sql/upgrade-plan.json` 和实际迁移记录执行尚未执行的增量，发布前运行只读结构预检。升级计划、校验和及 CI 验证规则见 `docs/数据库升级验证.md`。

全量表结构说明见 `docs/数据库表结构.md`（由建表脚本合并生成）。

## Git 工作流

- 用户要求提交或推送代码且未指定分支时，默认直接提交并推送到 `main` 分支
- 不主动创建功能分支或 Pull Request；只有用户明确要求时才创建

## 部署

### Docker 构建

```bash
# 构建镜像
docker build -t aio-life-server:latest .

# 运行容器（需要配置环境变量）
docker run -d \
  -p 45678:45678 \
  -e AIO_LIFE_DB_PASSWORD=your_password \
  -e AIO_LIFE_REDIS_HOST=redis_host \
  aio-life-server:latest
```

### 部署脚本

`deploy.sh` 提供自动化部署能力，需要配置环境变量：
- `AIO_LIFE_SERVER_IP` — 服务器 IP
- `AIO_LIFE_SERVER_USER` — SSH 用户名
- `AIO_LIFE_SERVER_PASSWORD` — SSH 密码

## 日志与监控

- **日志格式**：包含 `traceId` 和 `spanId`（Micrometer + Brave）
- **Prometheus 指标**：`/actuator/prometheus`（端口 45679）
- **健康检查**：`/actuator/health`（端口 45679）

## 注意事项

1. **数据库连接池**：使用 Druid，已配置空闲连接检测，防止被 MySQL 踢掉
2. **邮件验证码**：有频率限制（单 IP/单邮箱/全局），配置在 `aio.life.server.auth.code.*`
3. **定时任务**：`@EnableScheduling` 已启用，LeetCode 同步 cron 可配置
4. **Neo4j 可选**：默认关闭，通过环境变量 `AIO_LIFE_NEO4J_ENABLED=true` 启用
5. **MCP 端点**：`/api/mcp`，使用 `streamable-http` 传输协议，复用现有认证
