# AGENTS.md

- 系统尚未正式上线，应用只维护当前数据结构和接口契约，不保留旧数据格式、旧客户端、旧参数或旧缓存的兼容分支。数据库结构变更必须提供已有库可执行的增量脚本，并同步实体和两端调用方；日常新增 Flyway `V` 迁移，不改写已执行脚本；由用户在选定时间节点生成完整的 `B` 基线，并保留已有环境仍需的增量。此前清理历史 SQL 仅针对已整理的旧脚本，不代表后续停止维护增量迁移。

This file provides guidance to AI coding agents when working with code in this repository.

## 项目概述

**AIO Life Server** — All-in-One 人生管理系统的后端服务，记录、统计、分析人生的所有数据。

## 技术栈

- **Java 21** + **Spring Boot 3.5.16**
- **MyBatis Plus 3.5.11** + **Druid 1.2.24** (ORM + 连接池)
- **PostgreSQL 18+**（版本及排序规则见 [SQL 脚本规范](docs/SQL脚本规范.md)）
- **Redis** (分布式缓存、Session、Sa-Token 存储)
- **Caffeine** (本地内存缓存，配合 Spring Cache 作为二级缓存)
- **Sa-Token 1.40.0** (认证授权，JWT 模式)
- **MinIO** (对象存储)
- **Neo4j** (关系图谱，可选模块)
- **LangChain4j 1.12.2** + **MCP 0.14.0** (MCP 工具注册与 Schema 适配)
- **MapStruct 1.6.0** (对象映射)
- **Hutool 5.8.36** (Java 工具库)
- **fastjson2 2.0.66** (JSON 处理)
- **jsoup 1.23.2** (HTML 解析，用于第三方数据同步)
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
| `membership` | 会员平台、订阅记录与统计 |
| `feedback` | 用户反馈、评论与管理端处理 |
| `relationship` | 人际关系图谱（Neo4j），通过 `AIO_LIFE_NEO4J_ENABLED` 环境变量控制 |
| `mcp` | MCP 协议支持：自定义注解驱动的 Tool 注册，含认证层 |
| `core` | 公共组件：ApiResponse、异常处理、工具类、常量 |
| `config` | 全局配置：Cache、CORS、JSON、MinIO、MyBatis Plus、Neo4j、Redis、Sa-Token |

公共 Redis 封装放在 `core/redis`；`core` 不依赖业务模块的实体、Mapper 或服务。系统菜单权威读取由 `system/service/MenuAccessReader` 负责，二级锁菜单解析与业务映射归 `sso/service/SecondaryLockMenuResolver`、`SecondaryLockPolicy`；这些授权读取直接查询当前数据，不增加本地或 Redis 缓存。该依赖边界由 `CoreDependencyArchitectureTest` 检查。

LLM 会话、消息和模型密钥业务接口、数据表及实体 Mapper 已移除，不恢复已取消的业务。

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

- **逻辑删除**：实体通过 `@TableLogic` 显式标注 `isDeleted`（0=未删除，1=已删除），不依赖全局字段名配置
- **ID 生成**：使用雪花算法 `IdType.ASSIGN_ID`
- **基础实体**：所有业务实体继承 `BaseEntity`，包含 `id`、`createUser`、`createTime`、`updateUser`、`updateTime`、`isDeleted` 字段

### 数据库访问规范（强制）

- PostgreSQL 业务数据访问统一使用 **MyBatis-Plus / MyBatis Mapper**。禁止在生产业务代码中使用 `JdbcTemplate`、`NamedParameterJdbcTemplate`、`JdbcClient` 或直接 JDBC 连接执行 SQL；不能因联表、行锁、性能或编写方便另起一套访问方式。
- 普通单表增删改查优先使用 `BaseMapper<Entity>` 和类型安全的 Wrapper。联表、`FOR UPDATE`、特殊审计更新等 SQL 放在 `mapper/` 的注解方法或对应 Mapper XML 中，参数使用 `#{...}` 绑定；禁止将用户输入拼接为 SQL。
- Controller、Service、Guard 只负责接口、业务规则和事务编排，不允许内嵌 SQL。需要读取软删除记录时，在 Mapper 中显式编写 SQL 并说明原因，不能改用 JDBC 绕开逻辑删除。
- 新增业务表必须同时提供准确的 `@TableName` 实体与 `BaseMapper`，通过 `PhysicalTableEntitySelectIntegrationTest` 的真实 PostgreSQL 全字段查询。禁止添加 JDBC 表清单或跳过表来绕过检查。
- 迁移数据库访问方式必须保留用户归属、逻辑删除、审计字段、锁顺序和事务边界。需要清空的 nullable 字段使用显式 `set(..., null)` 或字段级 `FieldStrategy.ALWAYS`；不得全局启用空值覆盖。
- `FOR UPDATE` 等要求重新访问数据库的查询必须禁用查询缓存，并刷新一级缓存（如 `@Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)`），避免锁查询被缓存结果替代。
- 测试夹具、数据库结构检查可以使用 JDBC；数据源和事务管理配置不属于业务查询。`PersistenceArchitectureTest` 自动检查生产源码边界，不允许为业务代码添加豁免。

### API 响应格式

统一返回 `ApiResponse<T>`，成功示例：

```json
{
  "code": 0,
  "message": null,
  "data": null
}
```

`data` 按接口返回对象、数组、标量或 `null`；HTTP 200 不代表业务成功。

业务实际数量响应（卡面使用数、会员/衣柜统计、消息未读数）使用 `Integer`，输出 JSON 数字；Java `long` 计数在响应边界用 `Math.toIntExact()` 转换，禁止静默截断。ID 继续使用 `Long` 并输出字符串；`PageResp.total` 保留 `Long + CountSerializer` 的数字分页契约。

### 认证

- **Sa-Token JWT**：Token 通过 `Authorization: Bearer <token>` 传递
- **获取当前用户**：`StpUtil.getLoginIdAsLong()`
- **登录拦截**：默认拦截所有请求，白名单在 `SaTokenConfig` 配置

### MCP Tool 开发

工具方法使用零参数或单个 DTO 参数；DTO 字段通过 `@Description` 描述，并声明实际需要的 Bean Validation 约束。调用器将整个 `arguments` 对象转换为 DTO，不使用单个 `String` 等标量参数承接字段。

```java
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.mcp.annotation.McpToolProvider;

@McpToolProvider  // 标记为 MCP 工具提供者
public class MyMcpTools {

    @Getter
    @Setter
    public static class MyToolReq {
        @Description("关键词，不可为空白")
        @NotBlank
        private String keyword;
    }

    @Tool("返回已校验的关键词，演示 DTO 入参")
    public String myTool(MyToolReq req) {
        return req.getKeyword();
    }
}
```

示例调用参数为 `{"keyword":"示例"}`。实际业务工具复用 Service，并登记相应的二级锁策略；参考 [GoalMcpTools](src/main/java/top/aiolife/record/mcp/GoalMcpTools.java) 与[接口范围](../docs/需求/MCP接口需求方案.md)。

### 对象映射

- PostgreSQL 持久化实体统一继承 `IdentityEntity`（可经 `AuditEntity` / `BaseEntity`），使用其不可覆盖的身份比较：运行时类型相同且非空主键相同才相等；无主键时仅自身相等。主键赋值后不得修改，业务、审计和逻辑删除字段不参与比较。哈希值按类型固定，避免分配主键后破坏集合查找；大量记录的索引与去重显式使用主键作为 Map / Set 的键。
- 实体使用 `@Getter` / `@Setter`，需要时单独使用 `@ToString` 并保留敏感字段排除，不使用 `@Data` 或 Lombok `@EqualsAndHashCode` 重新生成比较方法。DTO / VO 使用值比较时，继承的值对象应显式包含父类字段；不要套用实体的身份规则或通过全局配置屏蔽警告。

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
- 对外接口使用简短中文 `@Operation(summary = "动作＋业务对象")`，避免仅写“新增”“删除”；`description` 只补日期格式、统计范围、副作用等必要口径，不重复通用响应和鉴权规则。接口文档随实现维护，不另建静态接口清单。
- 异常不主动捕捉，交由全局 `@RestControllerAdvice` 处理

## 环境变量

敏感配置通过环境变量注入，命名规范 `AIO_LIFE_*`：

| 变量 | 说明 | 默认值 |
|---|---|---|
| `AIO_LIFE_JWT_SECRET` | JWT 签名密钥（Sa-Token），生产必须覆盖 | aio_life（仅本地开发） |
| `AIO_LIFE_DB_PASSWORD` | 数据库密码 | root（仅开发） |
| `AIO_LIFE_DB_USERNAME` | 数据库用户 | aio_life |
| `AIO_LIFE_DB_NAME` | 数据库名称 | aio_life |
| `AIO_LIFE_DB_SSLMODE` | PostgreSQL TLS 模式 | prefer |
| `AIO_LIFE_DB_URL` | 数据库地址 | 127.0.0.1:5432 |
| `AIO_LIFE_REDIS_HOST` | Redis 地址 | 127.0.0.1 |
| `AIO_LIFE_REDIS_PORT` | Redis 端口 | 6379 |
| `AIO_LIFE_REDIS_PASSWORD` | Redis 密码 | 空 |
| `AIO_LIFE_MINIO_ENDPOINT` | MinIO 地址 | http://localhost:1300 |
| `AIO_LIFE_MINIO_ACCESS_KEY` | MinIO AK | aio_life |
| `AIO_LIFE_MINIO_SECRET_KEY` | MinIO SK | aio_life |
| `AIO_LIFE_MINIO_BUCKET_NAME` | MinIO Bucket | aiolife |
| `AIO_LIFE_NEO4J_ENABLED` | Neo4j 开关 | false |
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

Mockito 通过 Maven Surefire 在测试 JVM 启动时加载代理；`maven-dependency-plugin:properties` 解析实际依赖 JAR 路径，不依赖动态挂载或放宽 JVM 动态代理限制。优先用 `mvn test`（或 `-Dtest=...`）执行，以保留这套启动配置。

`RelationshipElementIdIntegrationTest` 自建隔离 Neo4j 5.26.0，验证关系 ID 在创建、详情和更新接口间原样往返，以及跨用户更新拒绝。需要 Docker，不访问业务图数据库；本地已有其他版本镜像时可通过 `-Daio.life.test.neo4j.image=neo4j:<version>` 显式指定测试镜像，交付时注明实际验证版本。

## 数据库初始化与增量升级

仅支持 PostgreSQL 18+，不维护旧数据库运行兼容。`sql/migrations/` 是唯一当前 Flyway 发现目录；初始结构为 `V20261110_01__postgresql_schema.sql`（68 张业务表），公共种子按用途拆为 `V20261110_02_001` 至 `V20261110_02_011`，目录及顺序见 `sql/README.md`。旧库结构、数据及历史修正 SQL 均不保留在工作树中，原文件仅通过 Git 历史追溯；当前 SQL 只维护 PostgreSQL。日常不改写已执行的 PostgreSQL 迁移，新结构或数据变更新增更高版本 `V`。

通过 `mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate` 独立迁移，使用 `FLYWAY_URL`、`FLYWAY_USER`、`FLYWAY_PASSWORD`；应用启动不迁移。新增更高版本 V，不能自动 baseline、清库或跳过失败。未知非空结构先只读预检，显式 baseline 必须提供匹配的 v6 PostgreSQL `schema.contract`。

`mvn -f tools/flyway/pom.xml verify` 创建隔离 PostgreSQL，验证初始化、重复 migrate、增量数据保留、事务失败回滚、结构契约与非空库拒绝。实体必须通过真实 PostgreSQL 全字段查询。H2 仅用于局部单元夹具，不能证明 PostgreSQL 方言正确。

数字状态列保持 smallint/integer，Boolean 实体由 MyBatis 类型处理器绑定 0/1；JSON 用 jsonb，二进制用 bytea，本地日期时间用 timestamp without time zone。JDBC 连接中 `stringtype=unspecified` 使 JSON 字符串按目标类型绑定；CONCAT 等变参函数中的参数显式 CAST。需要清空字段时显式 set null，同一 UPDATE 不得对一列重复赋值；可忽略的唯一键冲突使用 Mapper 的 ON CONFLICT，不能捕获错误后继续已中止事务。

更新操作由应用显式维护审计时间，不能依赖列的 DEFAULT 自动更新；保留笔记、闪念仅切换隐藏状态不改变内容时间的业务语义。数字状态 DTO 对应的 SQL 表达式显式返回 0/1，不能直接投影 PostgreSQL boolean。可空字段排序通过 `QueryOrder` 明确空值位置，分页继续以唯一 ID 兜底；原生 SQL 同样显式写 NULLS FIRST/LAST。`PostgresqlRegressionIntegrationTest` 使用 Testcontainers 自建 PostgreSQL 18，不读取业务连接配置，执行该测试需要 Docker。

当前表结构、初始化和一次性数据搬迁说明见 [数据库表结构](docs/数据库表结构.md)、[数据库初始化与验证](docs/数据库初始化与验证.md)、[SQL 脚本规范](docs/SQL脚本规范.md)。

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

### 部署入口

镜像构建与发布配置见 `.github/workflows/docker-publish.yml`；容器运行步骤见 [README](README.md#使用-docker-部署)。镜像发布不等于目标环境已部署。仓库当前没有 `deploy.sh`，不要依赖历史 SSH 部署说明。

## 日志与监控

- **日志格式**：包含 `traceId` 和 `spanId`（Micrometer + Brave）
- **Prometheus 指标**：`/actuator/prometheus`（端口 45679）
- **健康检查**：`/actuator/health`（端口 45679）

## 注意事项

1. **数据库连接池**：使用 Druid，已配置空闲连接检测，回收 PostgreSQL 失效连接
2. **邮件验证码**：有频率限制（单 IP/单邮箱/全局），配置在 `aio.life.server.auth.code.*`
3. **定时任务**：`@EnableScheduling` 已启用，LeetCode 同步 cron 可配置
4. **Neo4j 可选**：默认关闭，通过环境变量 `AIO_LIFE_NEO4J_ENABLED=true` 启用
5. **MCP 端点**：`/api/mcp`，使用 `streamable-http` 传输协议，复用现有认证
