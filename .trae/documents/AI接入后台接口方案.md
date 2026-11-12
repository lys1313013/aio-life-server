# AI接入后台接口方案

## 一、项目现状分析

### 1.1 技术栈
- **后端框架**: Java 21 + Spring Boot 3.3.10
- **AI集成**: LangChain4j 1.12.2（已集成）
- **数据库**: PostgreSQL + MyBatis Plus
- **权限认证**: Sa-Token
- **缓存**: Redis + Caffeine
- **对象存储**: MinIO
- **监控**: Prometheus + Micrometer

### 1.2 现有API资源（30个Controller）
| 模块 | 主要功能 |
|------|---------|
| 任务管理 | TaskController, TaskDetailController, TaskColumnController |
| 时间记录 | TimeRecordController, TimeTrackerCategoryController |
| 数据统计 | DashboardController, ExpController, IncomeController |
| 目标里程碑 | GoalController, MilestoneController |
| 运动健康 | ExerciseRecordController, MbtiController |
| 学习记录 | LeetcodeController, BVideoController |
| 笔记系统 | ThoughtController, MemoController |
| 系统字典 | SysDictTypeController, SysDictDataController |
| 用户系统 | UserController, AuthController, MessageController |

### 1.3 现有LLM能力
- ✅ 基础聊天功能（同步/流式SSE）
- ✅ API Key管理（多模型支持）
- ✅ 会话和历史记录管理
- ✅ 时间记录总结功能
- ❌ **Function Calling / Tool Use（缺失）**
- ❌ **AI调用后台接口能力（缺失）**

---

## 二、方案设计

### 2.1 方案对比

我们有三种主流方案可以实现 AI 调用后台接口：

#### 方案A：LangChain4j @Tool 注解（已详细说明）

**技术栈**：LangChain4j 1.12.2（项目已集成）

| 优势 | 劣势 |
|------|------|
| ✅ 已集成，快速上手 | ⚠️ 绑定 LangChain4j 生态 |
| ✅ 自动扫描和注册工具 | ⚠️ 工具定义依赖 Java 注解 |
| ✅ 内置参数验证 | ⚠️ 不支持标准 MCP 协议 |
| ✅ 与 Spring Boot 完美集成 | ⚠️ 迁移到其他框架成本高 |
| ✅ 代码简洁易维护 |  |

---

#### 方案B：MCP (Model Context Protocol) ⭐ **强烈推荐**

**技术栈**：MCP Java SDK（原生实现，不使用 Spring AI）

MCP 是一种新兴的标准协议，由 Anthropic 开发，得到了广泛支持：
- **官方支持**：OpenAI、Claude、Cursor、VS Code 等
- **Java 生态**：ModelContextProtocol/java-sdk（原生 SDK）
- **标准化**：工具定义遵循统一规范，易于扩展

| 优势 | 劣势 |
|------|------|
| ✅ **标准化协议**，生态完善 | ⚠️ 需要引入新依赖 |
| ✅ **一次定义，多端使用**（Claude、Cursor、本地AI等） | ⚠️ 学习曲线（但不高） |
| ✅ **工具发现机制**，AI 自动感知可用工具 | ⚠️ 部分功能还在快速发展中 |
| ✅ **支持多种传输方式**（STDIO、SSE、Streamable HTTP） |  |
| ✅ **原生 Java SDK**，轻量级、无框架依赖 |  |
| ✅ **社区活跃**，持续更新 |  |
| ✅ **前后端解耦**，可独立部署 MCP Server |  |
| ✅ **与现有 LangChain4j 代码共存**，不冲突 |  |

**架构图**：

```
┌─────────────────────────────────────────────────────────┐
│              AI Client (Claude/Cursor/其他)              │
└──────────────────┬────────────────────────────────────┘
                   │ MCP Protocol (标准化通信)
                   ▼
┌─────────────────────────────────────────────────────────┐
│        MCP Server (MCP Java SDK 原生实现)                │
│  ┌─────────────────────────────────────────────────┐   │
│  │         Tool Registry (手动注册)                  │   │
│  │  • ListToolsHandler (工具列表)                   │   │
│  │  • CallToolHandler (工具调用)                    │   │
│  └─────────────────────────────────────────────────┘   │
│                         │                              │
│                         ▼                              │
│  ┌─────────────────────────────────────────────────┐   │
│  │         Business Services                         │   │
│  │  • TaskService (任务管理)                        │   │
│  │  • TimeRecordService (时间记录)                  │   │
│  │  • GoalService (目标管理)                        │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

---

#### 方案C：手动实现 Function Calling

**技术栈**：直接使用 OpenAI/Claude API

| 优势 | 劣势 |
|------|------|
| ✅ 完全控制 | ❌ 代码量大 |
| ✅ 无框架依赖 | ❌ 维护成本高 |
| ✅ 轻量级 | ❌ 需要手动管理工具定义 |
|  | ❌ 参数验证需自行实现 |

---

### 2.2 推荐方案

**强烈推荐：方案B - MCP (Model Context Protocol)**

理由：
1. **标准化**：MCP 是 AI 工具调用的未来标准
2. **生态完善**：Spring AI 官方支持，社区活跃
3. **易维护**：注解驱动，自动发现工具
4. **灵活性**：可独立部署，也可内嵌
5. **未来可扩展**：支持多 AI 客户端（Claude、Cursor、Copilot 等）

### 2.2 架构设计

```
┌─────────────────────────────────────────────────────────┐
│                    AI Assistant                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │            System Prompt (角色设定)              │   │
│  │  "你是一个个人助手，可以调用以下工具来..."       │   │
│  └─────────────────────────────────────────────────┘   │
│                         │                              │
│                         ▼                              │
│  ┌─────────────────────────────────────────────────┐   │
│  │            LLM (大模型)                         │   │
│  │  • 分析用户意图                                 │   │
│  │  • 决定是否调用工具                              │   │
│  │  • 解析工具返回结果                              │   │
│  └─────────────────────────────────────────────────┘   │
│                         │                              │
│                         ▼                              │
│  ┌─────────────────────────────────────────────────┐   │
│  │            Tool Registry                        │   │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐        │   │
│  │  │ TaskTool│ │TimeTool │ │GoalTool │  ...    │   │
│  │  └─────────┘ └─────────┘ └─────────┘        │   │
│  └─────────────────────────────────────────────────┘   │
│                         │                              │
│                         ▼                              │
│  ┌─────────────────────────────────────────────────┐   │
│  │          Business Services                       │   │
│  │  • TaskService                                  │   │
│  │  • TimeRecordService                            │   │
│  │  • GoalService                                  │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

---

## 三、实施方案

### 阶段一：基础设施搭建

#### 3.1.1 添加必要依赖

修改 `pom.xml`，添加 LangChain4j Spring Boot Starter：

```xml
<!-- LangChain4j Spring Boot Starter -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-spring-boot-starter</artifactId>
    <version>1.12.2</version>
</dependency>
```

#### 3.1.2 创建工具基类

创建 `src/main/java/top/aiolife/llm/tools/AbstractTool.java`：

```java
package top.aiolife.llm.tools;

import cn.dev33.satoken.stp.StpUtil;

/**
 * AI工具基类
 * 提供通用方法和上下文信息
 */
public abstract class AbstractTool {

    /**
     * 获取当前登录用户ID
     */
    protected long getUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /**
     * 获取当前用户名
     */
    protected String getUsername() {
        return StpUtil.getLoginId().toString();
    }
}
```

### 阶段二：工具开发

#### 3.2.1 任务管理工具

创建 `src/main/java/top/aiolife/llm/tools/TaskTools.java`：

```java
package top.aiolife.llm.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.aiolife.record.pojo.entity.TaskEntity;
import top.aiolife.record.service.ITaskService;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskTools extends AbstractTool {

    private final ITaskService taskService;

    @Tool("获取用户的所有任务列表")
    public List<TaskEntity> getTasks(
            @ToolParameter(description = "页码，默认1") int page,
            @ToolParameter(description = "每页数量，默认100") int pageSize) {
        return taskService.list(null);
    }

    @Tool("创建新任务")
    public TaskEntity createTask(
            @ToolParameter(description = "任务名称") String title,
            @ToolParameter(description = "任务描述") String description,
            @ToolParameter(description = "所属列ID") Long columnId) {
        TaskEntity task = new TaskEntity();
        task.setUserId(getUserId());
        task.setTitle(title);
        task.setDescription(description);
        task.setColumnId(columnId);
        return taskService.save(task);
    }

    @Tool("更新任务状态")
    public void updateTaskStatus(
            @ToolParameter(description = "任务ID") Long taskId,
            @ToolParameter(description = "新状态：TODO, IN_PROGRESS, DONE") String status) {
        taskService.updateStatus(taskId, getUserId(), status);
    }

    @Tool("删除任务")
    public void deleteTask(
            @ToolParameter(description = "任务ID") Long taskId) {
        taskService.removeById(taskId, getUserId());
    }
}
```

#### 3.2.2 时间记录工具

创建 `src/main/java/top/aiolife/llm/tools/TimeRecordTools.java`：

```java
package top.aiolife.llm.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.service.ITimeRecordService;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimeRecordTools extends AbstractTool {

    private final ITimeRecordService timeRecordService;

    @Tool("查询指定日期的时间记录")
    public List<TimeRecordEntity> getTimeRecords(
            @ToolParameter(description = "日期，格式：yyyy-MM-dd") String date) {
        return timeRecordService.listByDate(getUserId(), LocalDate.parse(date));
    }

    @Tool("查询本周的时间记录")
    public List<TimeRecordEntity> getWeekTimeRecords() {
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.minusDays(today.getDayOfWeek().getValue() - 1);
        LocalDate endOfWeek = startOfWeek.plusDays(6);
        return timeRecordService.listByDateRange(getUserId(), startOfWeek, endOfWeek);
    }

    @Tool("添加时间记录")
    public void addTimeRecord(
            @ToolParameter(description = "日期") String date,
            @ToolParameter(description = "开始时间 HH:mm") String startTime,
            @ToolParameter(description = "结束时间 HH:mm") String endTime,
            @ToolParameter(description = "分类ID") String categoryId,
            @ToolParameter(description = "标题") String title,
            @ToolParameter(description = "描述") String description) {
        timeRecordService.addTimeRecord(getUserId(), date, startTime, endTime, categoryId, title, description);
    }

    @Tool("获取今日时间分配统计")
    public String getTodayTimeSummary() {
        return timeRecordService.getSummary(getUserId(), LocalDate.now());
    }
}
```

#### 3.2.3 目标管理工具

创建 `src/main/java/top/aiolife/llm/tools/GoalTools.java`：

```java
package top.aiolife.llm.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.aiolife.record.pojo.entity.GoalEntity;
import top.aiolife.record.service.IGoalService;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoalTools extends AbstractTool {

    private final IGoalService goalService;

    @Tool("获取用户的所有目标")
    public List<GoalEntity> getGoals(
            @ToolParameter(description = "目标状态过滤：ACTIVE, COMPLETED, ALL") String status) {
        return goalService.listByUser(getUserId(), status);
    }

    @Tool("创建新目标")
    public GoalEntity createGoal(
            @ToolParameter(description = "目标名称") String name,
            @ToolParameter(description = "目标描述") String description,
            @ToolParameter(description = "目标类型：DAILY, WEEKLY, MONTHLY, YEARLY") String type,
            @ToolParameter(description = "目标数值") int targetValue,
            @ToolParameter(description = "单位") String unit) {
        return goalService.createGoal(getUserId(), name, description, type, targetValue, unit);
    }

    @Tool("更新目标进度")
    public void updateGoalProgress(
            @ToolParameter(description = "目标ID") Long goalId,
            @ToolParameter(description = "当前完成值") int currentValue) {
        goalService.updateProgress(goalId, getUserId(), currentValue);
    }
}
```

#### 3.2.4 数据统计工具

创建 `src/main/java/top/aiolife/llm/tools/StatsTools.java`：

```java
package top.aiolife.llm.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.aiolife.record.pojo.vo.DashboardCardVO;
import top.aiolife.record.service.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatsTools extends AbstractTool {

    private final IExerciseRecordService exerciseService;
    private final ILeetcodeService leetcodeService;
    private final IGithubService githubService;

    @Tool("获取今日健康数据统计")
    public DashboardCardVO getTodayExerciseStats() {
        return exerciseService.getTodayStats(getUserId());
    }

    @Tool("获取今日刷题统计")
    public String getTodayLeetcodeStats() {
        return leetcodeService.getTodaySummary(getUserId());
    }

    @Tool("获取GitHub贡献统计")
    public String getGithubContributionStats(
            @ToolParameter(description = "天数，默认7天") int days) {
        return githubService.getContributionSummary(getUserId(), days);
    }

    @Tool("获取仪表盘概览")
    public DashboardCardVO getDashboardOverview() {
        return getDashboardCard("OVERVIEW");
    }
}
```

### 阶段三：AI服务配置

#### 3.3.1 创建系统提示词

创建 `src/main/java/top/aiolife/llm/prompt/LifeAssistantPrompt.java`：

```java
package top.aiolife.llm.prompt;

public class LifeAssistantPrompt {

    public static final String SYSTEM_PROMPT = """
        你是一个智能个人生活助手，可以帮助用户管理任务、记录时间、追踪目标等。

        ## 可用工具

        你可以使用以下工具来完成任务：

        ### 任务管理
        - getTasks: 查看任务列表
        - createTask: 创建新任务
        - updateTaskStatus: 更新任务状态
        - deleteTask: 删除任务

        ### 时间记录
        - getTimeRecords: 查询指定日期的时间记录
        - getWeekTimeRecords: 查询本周时间记录
        - addTimeRecord: 添加时间记录
        - getTodayTimeSummary: 获取今日时间分配统计

        ### 目标管理
        - getGoals: 查看目标列表
        - createGoal: 创建新目标
        - updateGoalProgress: 更新目标进度

        ### 数据统计
        - getTodayExerciseStats: 获取今日健康数据
        - getTodayLeetcodeStats: 获取今日刷题统计
        - getGithubContributionStats: 获取GitHub贡献统计
        - getDashboardOverview: 获取仪表盘概览

        ## 工作原则

        1. 当用户询问具体数据时，优先调用相关工具获取实时数据
        2. 当用户要求创建或更新数据时，先确认参数，然后调用对应工具
        3. 返回结果时要简洁明了，用中文回复
        4. 如果工具调用失败，诚实地告诉用户错误原因
        5. 不要编造不存在的数据，所有数据必须通过工具获取

        ## 交互示例

        用户: 今天我做了什么？
        助手: 让我查一下您的时间记录...
        [调用 getTodayTimeRecords 工具]
        您今天的时间记录：
        - 09:00-12:00: 项目开发
        - 14:00-15:00: 健身运动
        - 19:00-21:00: 学习英语

        用户: 帮我创建一个"完成论文"的任务
        助手: 好的，我来为您创建这个任务。
        [调用 createTask 工具]
        任务"完成论文"已创建成功！
        """;

    private LifeAssistantPrompt() {
    }
}
```

#### 3.3.2 创建AI服务接口

创建 `src/main/java/top/aiolife/llm/service/AILifeAssistant.java`：

```java
package top.aiolife.llm.service;

import dev.langchain4j.service.Agent;
import dev.langchain4j.service.SystemMessage;

@Agent
public interface AILifeAssistant {

    @SystemMessage(LifeAssistantPrompt.SYSTEM_PROMPT)
    String chat(String userMessage);
}
```

#### 3.3.3 创建工具执行服务

创建 `src/main/java/top/aiolife/llm/service/ToolExecutionService.java`：

```java
package top.aiolife.llm.service;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.lang.reflect.Method;

@Slf4j
@Service
public class ToolExecutionService implements ToolExecutor {

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public String execute(ToolExecutionRequest request) {
        try {
            // 解析工具名和方法名
            String toolName = request.name();
            String methodName = toolName; // 默认方法名与工具名相同

            // 从工具注册表中获取对应的Bean和方法
            Object toolBean = getToolBean(toolName);
            if (toolBean == null) {
                return "错误：未找到工具 " + toolName;
            }

            Method method = findMethod(toolBean, methodName, request.arguments());
            if (method == null) {
                return "错误：未找到方法 " + methodName;
            }

            // 执行方法
            Object result = method.invoke(toolBean, parseArguments(method, request.arguments()));
            return result != null ? result.toString() : "操作成功";

        } catch (Exception e) {
            log.error("工具执行失败: {}", e.getMessage(), e);
            return "执行失败：" + e.getMessage();
        }
    }

    private Object getToolBean(String toolName) {
        // 根据工具名获取对应的Bean
        return applicationContext.getBean(toolName + "Tools");
    }

    private Method findMethod(Object bean, String methodName, String arguments) {
        for (Method method : bean.getClass().getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    private Object[] parseArguments(Method method, String arguments) {
        // 解析JSON参数
        // ...
    }
}
```

### 阶段四：Controller增强

#### 3.4.1 创建AI助手Controller

修改 `src/main/java/top/aiolife/llm/api/LLMController.java`，添加：

```java
@Autowired(required = false)
private AILifeAssistant aiAssistant;

@PostMapping("/assistant")
public ApiResponse<String> assistant(@RequestBody Map<String, String> request) {
    try {
        long userId = StpUtil.getLoginIdAsLong();
        String userMessage = request.get("message");

        String response = aiAssistant.chat(userMessage);
        return ApiResponse.success(response);
    } catch (Exception e) {
        log.error("AI助手调用失败: {}", e.getMessage(), e);
        return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, e.getMessage());
    }
}

@PostMapping(value = "/assistant/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter assistantStream(@RequestBody Map<String, String> request) {
    // 流式响应实现
}
```

#### 3.4.2 获取可用工具列表

添加新接口：

```java
@GetMapping("/tools")
public ApiResponse<List<ToolInfo>> getAvailableTools() {
    // 返回所有可用的工具列表
    List<ToolInfo> tools = Arrays.asList(
        new ToolInfo("getTasks", "获取任务列表", "查看用户的所有任务"),
        new ToolInfo("createTask", "创建任务", "创建新的任务项"),
        new ToolInfo("getTimeRecords", "查询时间记录", "查询指定日期的时间记录"),
        new ToolInfo("addTimeRecord", "添加时间记录", "记录一段时间的活动"),
        // ... 其他工具
    );
    return ApiResponse.success(tools);
}
```

---

## 四、MCP 方案详细实施（方案B）

### 4.1 核心依赖配置

修改 `pom.xml`，添加 MCP Java SDK 原生依赖：

```xml
<!-- MCP Java SDK -->
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
    <version>0.14.1</version>
</dependency>

<!-- MCP JSON 处理 -->
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp-json</artifactId>
    <version>0.14.1</version>
</dependency>

<!-- 如果使用 SSE 传输 -->
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp-sse</artifactId>
    <version>0.14.1</version>
</dependency>

<!-- WebFlux（用于 SSE 传输，非必须） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

### 4.2 MCP Server 核心实现

MCP Java SDK 的核心是 `McpServer` 类，它提供了三个关键的 Handler：

#### 4.2.1 MCP Server 初始化

创建 `src/main/java/top/aiolife/mcp/McpServerStarter.java`：

```java
package top.aiolife.mcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * MCP Server 启动器
 * 使用 MCP Java SDK 原生实现
 */
@Component
public class McpServerStarter implements ApplicationListener<ApplicationReadyEvent> {

    private final McpServer syncMcpServer;

    public McpServerStarter(
            ListToolsHandler listToolsHandler,
            CallToolHandler callToolHandler
    ) {
        this.syncMcpServer = McpServer.sync(
                (McpServer.SyncBuilder builder) -> builder
                        .info(new McpSchema.ServerInfo(
                                "aio-life-server",
                                "1.0.0"
                        ))
                        .capabilities(new McpSchema.ServerCapabilities(
                                McpSchema.ToolsProviderCapability.of(true), // 启用工具
                                null, // 资源（可选）
                                null  // 提示（可选）
                        ))
                        .onListTools(listToolsHandler::handle)
                        .onCallTool(callToolHandler::handle)
        );
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // MCP Server 在构造时自动启动
    }
}
```

#### 4.2.2 工具列表 Handler

创建 `src/main/java/top/aiolife/mcp/ListToolsHandler.java`：

```java
package top.aiolife.mcp;

import cn.dev33.satoken.stp.StpUtil;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 工具列表处理器
 * 定义 AI 可调用的所有工具
 */
@Slf4j
@Component
public class ListToolsHandler {

    private final ToolRegistry toolRegistry;

    public ListToolsHandler(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public McpSchema.ListToolsResult handle(McpSchema.ListToolsRequest request) {
        try {
            // 验证用户登录
            if (!isUserLoggedIn()) {
                log.warn("用户未登录，拒绝访问工具列表");
                return new McpSchema.ListToolsResult(List.of());
            }

            // 获取所有注册的工具
            List<McpSchema.Tool> tools = toolRegistry.getAllTools();

            log.info("返回 {} 个可用工具", tools.size());
            return new McpSchema.ListToolsResult(tools);

        } catch (Exception e) {
            log.error("获取工具列表失败: {}", e.getMessage(), e);
            return new McpSchema.ListToolsResult(List.of());
        }
    }

    private boolean isUserLoggedIn() {
        try {
            StpUtil.checkLogin();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

#### 4.2.3 工具调用 Handler

创建 `src/main/java/top/aiolife/mcp/CallToolHandler.java`：

```java
package top.aiolife.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工具调用处理器
 * 执行 AI 请求的工具调用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallToolHandler {

    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public McpSchema.CallToolResult handle(McpSchema.CallToolRequest request) {
        String toolName = request.name();
        log.info("[MCP] 用户: {}, 调用工具: {}", getCurrentUserId(), toolName);

        try {
            // 验证用户登录
            if (!isUserLoggedIn()) {
                return new McpSchema.CallToolResult(
                        null,
                        McpSchema.CallToolResult.ResultContentType.Error,
                        "用户未登录，请先登录"
                );
            }

            // 获取工具
            ToolDefinition tool = toolRegistry.getTool(toolName);
            if (tool == null) {
                log.warn("工具不存在: {}", toolName);
                return new McpSchema.CallToolResult(
                        null,
                        McpSchema.CallToolResult.ResultContentType.Error,
                        "工具不存在: " + toolName
                );
            }

            // 执行工具
            long startTime = System.currentTimeMillis();
            Object result = tool.execute(request.arguments());
            long duration = System.currentTimeMillis() - startTime;

            log.info("[MCP] 工具: {} 执行成功, 耗时: {}ms", toolName, duration);

            // 转换结果
            JsonNode resultJson = objectMapper.valueToTree(result);

            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(
                            McpSchema.CallToolResult.ResultContentType.Text,
                            resultJson.toString()
                    )),
                    null,
                    null
            );

        } catch (Exception e) {
            log.error("[MCP] 工具: {} 执行失败: {}", toolName, e.getMessage(), e);
            return new McpSchema.CallToolResult(
                    null,
                    McpSchema.CallToolResult.ResultContentType.Error,
                    "工具执行失败: " + e.getMessage()
            );
        }
    }

    private boolean isUserLoggedIn() {
        try {
            StpUtil.checkLogin();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private long getCurrentUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return -1;
        }
    }
}
```

/**
 * MCP Server 配置
 */
@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider toolCallbackProvider(Object[] tools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
    }
}
```

### 4.3 工具注册表

创建 `src/main/java/top/aiolife/mcp/ToolRegistry.java`：

```java
package top.aiolife.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 工具注册表
 * 统一管理所有可用的工具
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    public ToolRegistry(List<ToolProvider> toolProviders) {
        // 从所有 ToolProvider 注册工具
        for (ToolProvider provider : toolProviders) {
            for (ToolDefinition tool : provider.getTools()) {
                tools.put(tool.getName(), tool);
                log.info("注册 MCP 工具: {}", tool.getName());
            }
        }
        log.info("共注册 {} 个 MCP 工具", tools.size());
    }

    public ToolDefinition getTool(String name) {
        return tools.get(name);
    }

    public List<McpSchema.Tool> getAllTools() {
        List<McpSchema.Tool> result = new ArrayList<>();
        for (ToolDefinition tool : tools.values()) {
            result.add(tool.toMcpTool());
        }
        return result;
    }

    public int getToolCount() {
        return tools.size();
    }
}
```

### 4.4 MCP 工具开发

#### 4.4.1 工具定义类

创建 `src/main/java/top/aiolife/mcp/ToolDefinition.java`：

```java
package top.aiolife.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * MCP 工具定义
 */
@Data
@Builder
public class ToolDefinition {

    private String name;
    private String description;
    private List<ToolParameter> parameters;
    private Function<Map<String, Object>, Object> executor;

    @Data
    @Builder
    public static class ToolParameter {
        private String name;
        private String description;
        private String type;  // string, number, boolean, integer
        private boolean required;
        private Object defaultValue;
    }

    /**
     * 转换为 MCP Schema
     */
    public McpSchema.Tool toMcpTool() {
        List<McpSchema.Tool.InputSchema.Property> properties = parameters.stream()
                .map(param -> new McpSchema.Tool.InputSchema.Property(
                        param.getName(),
                        McpSchema.Tool.InputSchema.PropertyType.valueOf(param.getType().toUpperCase()),
                        param.getDescription(),
                        param.getRequired()
                ))
                .toList();

        McpSchema.Tool.InputSchema inputSchema = McpSchema.Tool.InputSchema.builder()
                .type("object")
                .properties(properties)
                .required(parameters.stream()
                        .filter(ToolParameter::isRequired)
                        .map(ToolParameter::getName)
                        .toList())
                .build();

        return McpSchema.Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(inputSchema)
                .build();
    }

    /**
     * 执行工具
     */
    public Object execute(JsonNode arguments) throws Exception {
        Map<String, Object> args = new java.util.HashMap<>();
        if (arguments != null && arguments.isObject()) {
            arguments.fields().forEachRemaining(entry ->
                    args.put(entry.getKey(), entry.getValue()));
        }
        return executor.apply(args);
    }
}
```

#### 4.4.2 任务管理工具

创建 `src/main/java/top/aiolife/mcp/tools/TaskToolProvider.java`：

```java
package top.aiolife.mcp.tools;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.aiolife.mcp.ToolDefinition;
import top.aiolife.mcp.ToolProvider;
import top.aiolife.record.pojo.entity.TaskEntity;
import top.aiolife.record.service.ITaskService;

import java.util.List;
import java.util.Map;

/**
 * MCP 任务管理工具提供者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskToolProvider implements ToolProvider {

    private final ITaskService taskService;

    @Override
    public List<ToolDefinition> getTools() {
        return List.of(
                // 获取任务列表
                ToolDefinition.builder()
                        .name("get_tasks")
                        .description("获取当前用户的所有任务列表")
                        .parameters(List.of())
                        .executor(args -> {
                            long userId = StpUtil.getLoginIdAsLong();
                            return taskService.listByUser(userId);
                        })
                        .build(),

                // 创建任务
                ToolDefinition.builder()
                        .name("create_task")
                        .description("创建一个新的任务")
                        .parameters(List.of(
                                ToolDefinition.ToolParameter.builder()
                                        .name("title").type("string")
                                        .description("任务标题").required(true).build(),
                                ToolDefinition.ToolParameter.builder()
                                        .name("description").type("string")
                                        .description("任务描述").required(false).build(),
                                ToolDefinition.ToolParameter.builder()
                                        .name("columnId").type("integer")
                                        .description("所属列ID").required(false).build()
                        ))
                        .executor(args -> {
                            long userId = StpUtil.getLoginIdAsLong();
                            String title = (String) args.get("title");
                            String description = (String) args.get("description");
                            Object columnId = args.get("columnId");

                            TaskEntity task = new TaskEntity();
                            task.setUserId(userId);
                            task.setTitle(title);
                            task.setDescription(description);
                            if (columnId != null) {
                                task.setColumnId(Long.valueOf(columnId.toString()));
                            }

                            TaskEntity saved = taskService.save(task);
                            return Map.of(
                                    "success", true,
                                    "message", "任务创建成功",
                                    "taskId", saved.getId(),
                                    "title", saved.getTitle()
                            );
                        })
                        .build(),

                // 更新任务状态
                ToolDefinition.builder()
                        .name("update_task_status")
                        .description("更新任务状态")
                        .parameters(List.of(
                                ToolDefinition.ToolParameter.builder()
                                        .name("taskId").type("integer")
                                        .description("任务ID").required(true).build(),
                                ToolDefinition.ToolParameter.builder()
                                        .name("status").type("string")
                                        .description("新状态: TODO, IN_PROGRESS, DONE")
                                        .required(true).build()
                        ))
                        .executor(args -> {
                            long userId = StpUtil.getLoginIdAsLong();
                            Long taskId = Long.valueOf(args.get("taskId").toString());
                            String status = (String) args.get("status");

                            taskService.updateStatus(taskId, userId, status);
                            return Map.of(
                                    "success", true,
                                    "message", "任务状态已更新",
                                    "taskId", taskId,
                                    "status", status
                            );
                        })
                        .build(),

                // 删除任务
                ToolDefinition.builder()
                        .name("delete_task")
                        .description("删除指定的任务")
                        .parameters(List.of(
                                ToolDefinition.ToolParameter.builder()
                                        .name("taskId").type("integer")
                                        .description("要删除的任务ID").required(true).build()
                        ))
                        .executor(args -> {
                            long userId = StpUtil.getLoginIdAsLong();
                            Long taskId = Long.valueOf(args.get("taskId").toString());
                            taskService.removeById(taskId, userId);
                            return Map.of(
                                    "success", true,
                                    "message", "任务已删除",
                                    "taskId", taskId
                            );
                        })
                        .build()
        );
    }
}
```

#### 4.3.2 时间记录工具

创建 `src/main/java/top/aiolife/mcp/tools/TimeRecordMcpTools.java`：

```java
package top.aiolife.mcp.tools;

import cn.dev33.satoken.stp.StpUtil;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.service.ITimeRecordService;

import java.time.LocalDate;
import java.util.List;

/**
 * MCP 时间记录工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeRecordMcpTools {

    private final ITimeRecordService timeRecordService;

    @Tool("查询指定日期的时间记录")
    public List<TimeRecordEntity> getTimeRecords(String date) {
        long userId = StpUtil.getLoginIdAsLong();
        return timeRecordService.listByDate(userId, LocalDate.parse(date));
    }

    @Tool("查询本周的时间记录")
    public List<TimeRecordEntity> getWeekTimeRecords() {
        long userId = StpUtil.getLoginIdAsLong();
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.minusDays(today.getDayOfWeek().getValue() - 1);
        LocalDate endOfWeek = startOfWeek.plusDays(6);
        return timeRecordService.listByDateRange(userId, startOfWeek, endOfWeek);
    }

    @Tool("添加时间记录")
    public String addTimeRecord(
            String date,
            String startTime,
            String endTime,
            String categoryId,
            String title,
            String description) {
        long userId = StpUtil.getLoginIdAsLong();
        timeRecordService.addTimeRecord(userId, date, startTime, endTime, categoryId, title, description);
        return String.format("时间记录已添加: %s %s-%s %s", date, startTime, endTime, title);
    }

    @Tool("获取今日时间分配统计")
    public String getTodayTimeSummary() {
        long userId = StpUtil.getLoginIdAsLong();
        return timeRecordService.getSummary(userId, LocalDate.now());
    }
}
```

#### 4.3.3 目标管理工具

创建 `src/main/java/top/aiolife/mcp/tools/GoalMcpTools.java`：

```java
package top.aiolife.mcp.tools;

import cn.dev33.satoken.stp.StpUtil;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.aiolife.record.pojo.entity.GoalEntity;
import top.aiolife.record.service.IGoalService;

import java.util.List;

/**
 * MCP 目标管理工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoalMcpTools {

    private final IGoalService goalService;

    @Tool("获取用户的所有目标")
    public List<GoalEntity> getGoals(String status) {
        long userId = StpUtil.getLoginIdAsLong();
        return goalService.listByUser(userId, status);
    }

    @Tool("创建新目标")
    public String createGoal(
            String name,
            String description,
            String type,
            int targetValue,
            String unit) {
        long userId = StpUtil.getLoginIdAsLong();
        GoalEntity goal = goalService.createGoal(userId, name, description, type, targetValue, unit);
        return String.format("目标创建成功！ID: %d, 名称: %s", goal.getId(), goal.getName());
    }

    @Tool("更新目标进度")
    public String updateGoalProgress(Long goalId, int currentValue) {
        long userId = StpUtil.getLoginIdAsLong();
        goalService.updateProgress(goalId, userId, currentValue);
        return String.format("目标 %d 进度已更新为: %d", goalId, currentValue);
    }
}
```

#### 4.3.4 统计查询工具

创建 `src/main/java/top/aiolife/mcp/tools/StatsMcpTools.java`：

```java
package top.aiolife.mcp.tools;

import cn.dev33.satoken.stp.StpUtil;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.aiolife.record.pojo.vo.DashboardCardVO;
import top.aiolife.record.service.*;

/**
 * MCP 统计查询工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatsMcpTools {

    private final IExerciseRecordService exerciseService;
    private final ILeetcodeService leetcodeService;
    private final IGithubService githubService;

    @Tool("获取今日健康数据统计")
    public DashboardCardVO getTodayExerciseStats() {
        long userId = StpUtil.getLoginIdAsLong();
        return exerciseService.getTodayStats(userId);
    }

    @Tool("获取今日刷题统计")
    public String getTodayLeetcodeStats() {
        long userId = StpUtil.getLoginIdAsLong();
        return leetcodeService.getTodaySummary(userId);
    }

    @Tool("获取GitHub贡献统计")
    public String getGithubContributionStats(int days) {
        long userId = StpUtil.getLoginIdAsLong();
        return githubService.getContributionSummary(userId, days);
    }

    @Tool("获取仪表盘概览")
    public String getDashboardOverview() {
        long userId = StpUtil.getLoginIdAsLong();
        return String.format("今日概览：运动%d次，刷题%d道，GitHub提交%d次",
                getTodayExerciseStats().getCount(),
                0, // TODO: 实现获取
                0); // TODO: 实现获取
    }
}
```

### 4.4 MCP Controller（可选，用于直接访问）

创建 `src/main/java/top/aiolife/mcp/api/McpController.java`：

```java
package top.aiolife.mcp.api;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP Controller
 * 提供 MCP 服务状态和工具列表查询（HTTP API 方式）
 */
@RestController
@RequestMapping("/mcp")
public class McpController {

    private final ToolRegistry toolRegistry;

    public McpController(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "running");
        status.put("protocol", "MCP 0.14.1");
        status.put("transport", "SSE / Streamable HTTP");
        status.put("toolCount", toolRegistry.getToolCount());
        return status;
    }

    @GetMapping("/tools")
    public Map<String, Object> getTools() {
        Map<String, Object> result = new HashMap<>();
        List<McpSchema.Tool> tools = toolRegistry.getAllTools();

        List<Map<String, Object>> toolList = tools.stream()
                .map(tool -> Map.<String, Object>of(
                        "name", tool.name(),
                        "description", tool.description() != null ? tool.description() : ""
                ))
                .collect(Collectors.toList());

        result.put("tools", toolList);
        result.put("count", toolList.size());
        return result;
    }
}
```

### 4.5 HTTP 传输配置（可选）

MCP Java SDK 支持多种传输方式。如果需要通过 HTTP 暴露 MCP 端点，创建传输配置：

```java
package top.aiolife.mcp.transport;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerExchange;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.transport.StdioServerTransport;
import io.modelcontextprotocol.transport.Transport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MCP HTTP 传输适配器
 * 将 MCP Server 暴露为 HTTP/SSE 端点
 */
@Slf4j
@RestController
@RequestMapping("/mcp-http")
public class McpHttpTransport {

    private final McpSyncServer syncServer;
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public McpHttpTransport(McpServerStarter mcpServerStarter) {
        this.syncServer = mcpServerStarter.getServer();
    }

    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sseEndpoint(@RequestParam String sessionId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.put(sessionId, emitter);

        emitter.onCompletion(() -> emitters.remove(sessionId));
        emitter.onTimeout(() -> emitters.remove(sessionId));
        emitter.onError(e -> {
            log.error("SSE 错误: {}", e.getMessage());
            emitters.remove(sessionId);
        });

        return emitter;
    }

    @PostMapping("/message")
    public Map<String, Object> handleMessage(
            @RequestParam String sessionId,
            @RequestBody Map<String, Object> message
    ) {
        try {
            // 处理 MCP 消息
            // ...
            return Map.of("success", true);
        } catch (Exception e) {
            log.error("处理消息失败: {}", e.getMessage(), e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    private void sendToClient(String sessionId, String data) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().data(data));
            } catch (IOException e) {
                log.error("发送SSE失败: {}", e.getMessage());
                emitter.complete();
            }
        }
    }
}
```

        // TODO: 从 toolCallbackProvider 获取工具列表
        // for (ToolCallback callback : toolCallbackProvider.getToolCallbacks()) {
        //     tools.add(Map.of(
        //         "name", callback.toString(),
        //         "description", "..."
        //     ));
        // }

        result.put("tools", tools);
        result.put("count", tools.size());
        return result;
    }

    private int getToolCount() {
        return 12; // TODO: 动态获取
    }
}
```

### 4.5 MCP 客户端配置（供其他AI使用）

创建 `mcp-config.json`（适用于 Claude Desktop、Cursor 等 MCP 客户端）：

```json
{
  "mcpServers": {
    "aio-life": {
      "command": "curl",
      "args": ["http://localhost:45678/api/mcp"],
      "env": {
        "AIO_LIFE_TOKEN": "${AIO_LIFE_TOKEN}"
      }
    }
  }
}
```

**或者使用 STDIO 方式**（推荐本地开发）：

```json
{
  "mcpServers": {
    "aio-life": {
      "command": "java",
      "args": [
        "-jar",
        "aio-life-mcp-server.jar",
        "--server-url=http://localhost:45678/api"
      ],
      "env": {
        "AIO_LIFE_TOKEN": "${AIO_LIFE_TOKEN}"
      }
    }
  }
}
```

**Cursor IDE 配置示例**：

1. 打开 Cursor 设置 → MCP Servers
2. 添加新的 MCP Server：
   ```json
   {
     "aio-life": {
       "transport": "http",
       "url": "http://localhost:45678/api/mcp/message",
       "headers": {
         "Authorization": "Bearer {你的AIO_LIFE_TOKEN}"
       }
     }
   }
   ```

**Claude Desktop 配置**：

编辑 `~/.config/claude-desktop/mcp.json`：

```json
{
  "mcpServers": {
    "aio-life": {
      "command": "npx",
      "args": [
        "-y",
        "@modelcontextprotocol/server-http",
        "http://localhost:45678/api/mcp"
      ]
    }
  }
}
```

### 4.6 MCP 服务健康检查

创建 `src/main/java/top/aiolife/mcp/McpHealthIndicator.java`：

```java
package top.aiolife.mcp;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * MCP 服务健康检查
 */
@Component
public class McpHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        // 检查 MCP 服务是否正常运行
        try {
            // TODO: 检查必要的组件
            return Health.up()
                    .withDetail("protocol", "MCP 0.14.1")
                    .withDetail("status", "running")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
```

### 4.7 MCP 错误处理

创建 `src/main/java/top/aiolife/mcp/exception/McpExceptionHandler.java`：

```java
package top.aiolife.mcp.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * MCP 异常处理
 */
@Slf4j
@RestControllerAdvice
public class McpExceptionHandler {

    @ExceptionHandler(McpException.class)
    public ResponseEntity<Map<String, Object>> handleMcpException(McpException e) {
        log.error("MCP 异常: {}", e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "McpException",
                        "message", e.getMessage()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        log.error("未知异常: {}", e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "InternalError",
                        "message", "服务器内部错误"
                ));
    }
}
```

### 4.8 MCP 日志和监控

创建 `src/main/java/top/aiolife/mcp/aspect/McpToolAspect.java`：

```java
package top.aiolife.mcp.aspect;

import cn.dev33.satoken.stp.StpUtil;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * MCP 工具调用日志切面
 */
@Slf4j
@Aspect
@Component
public class McpToolAspect {

    @Around("@annotation(dev.langchain4j.agent.tool.Tool)")
    public Object logToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        long userId = getCurrentUserId();
        long startTime = System.currentTimeMillis();

        log.info("[MCP Tool] 用户: {}, 调用工具: {}", userId, methodName);

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            log.info("[MCP Tool] 用户: {}, 工具: {}, 耗时: {}ms, 状态: SUCCESS",
                    userId, methodName, duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[MCP Tool] 用户: {}, 工具: {}, 耗时: {}ms, 状态: FAILED, 错误: {}",
                    userId, methodName, duration, e.getMessage());
            throw e;
        }
    }

    private long getCurrentUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return -1;
        }
    }
}
```

### 4.9 MCP 安全配置

创建 `src/main/java/top/aiolife/mcp/security/McpSecurityConfig.java`：

```java
package top.aiolife.mcp.security;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MCP 安全检查
 */
@Slf4j
@Component
public class McpSecurityConfig {

    /**
     * 检查用户是否有权限调用特定工具
     */
    public boolean hasPermission(long userId, String toolName) {
        // TODO: 实现权限检查逻辑
        // 例如：敏感工具需要VIP权限
        if (toolName.contains("delete") || toolName.contains("sensitive")) {
            // return userService.hasVipPermission(userId);
            return true;
        }
        return true;
    }

    /**
     * 验证 MCP 请求的认证信息
     */
    public boolean validateAuth(String token) {
        try {
            // 验证 Sa-Token
            StpUtil.checkLogin();
            return true;
        } catch (Exception e) {
            log.warn("MCP 认证失败: {}", e.getMessage());
            return false;
        }
    }
}
```

---

## 四、进阶功能（LangChain4j 方案）

### 4.1 工具权限控制

```java
@Tool(value = "查看敏感数据", requiredPermission = "VIEW_SENSITIVE_DATA")
public String getSensitiveInfo() {
    // 只有授权用户才能调用
}
```

### 4.2 工具使用日志

```java
@Aspect
@Component
public class ToolUsageAspect {

    @Around("@annotation(dev.langchain4j.agent.tool.Tool)")
    public Object logToolUsage(ProceedingJoinPoint joinPoint) {
        String toolName = joinPoint.getSignature().getName();
        long userId = getCurrentUserId();
        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            logTool(userId, toolName, startTime, true, null);
            return result;
        } catch (Exception e) {
            logTool(userId, toolName, startTime, false, e.getMessage());
            throw e;
        }
    }
}
```

### 4.3 工具调用限流

```java
@RateLimiter(value = 10, timeout = 60)
@Tool("查询敏感数据")
public String getSensitiveData() {
    // 每分钟最多调用10次
}
```

### 4.4 工具调用缓存

```java
@Tool("获取统计数据")
@Cacheable(value = "stats", key = "#userId + '_' + #date")
public Stats getDailyStats(long userId, String date) {
    // 缓存统计结果
}
```

---

## 五、实施计划

### 选择你的方案

#### 方案A：LangChain4j 实施计划（快速集成）

**第一阶段：基础框架（1-2天）**
1. 添加 LangChain4j Spring Boot Starter 依赖
2. 创建工具基类 AbstractTool
3. 创建 2-3 个核心工具（TaskTools, TimeRecordTools, GoalTools）
4. 配置 AI 服务接口
5. 基本功能测试

**第二阶段：功能完善（2-3天）**
1. 完成所有模块的工具开发
2. 实现流式响应
3. 添加错误处理和日志
4. 实现工具权限控制
5. 性能优化

**第三阶段：测试优化（1-2天）**
1. 功能测试
2. 性能测试
3. 异常场景测试
4. 用户体验优化
5. 文档完善

**预计总工期：4-7 天**

---

#### 方案B：MCP 实施计划（标准化方案）⭐ 推荐

**第一阶段：环境准备（0.5天）**
1. 添加 MCP Java SDK 依赖到 `pom.xml`
   - `io.modelcontextprotocol.sdk:mcp`
   - `io.modelcontextprotocol.sdk:mcp-json`
2. 配置 MCP Server 核心类
3. 验证 MCP 服务启动

**第二阶段：工具开发（2-3天）**
1. 创建核心工具类
   - `ToolRegistry` - 工具注册表
   - `TaskToolProvider` - 任务管理工具
   - `TimeRecordToolProvider` - 时间记录工具
   - `GoalToolProvider` - 目标管理工具
   - `StatsToolProvider` - 统计查询工具
2. 配置工具自动注册
3. 添加 Sa-Token 认证集成
4. 基本功能测试

**第三阶段：高级功能（1-2天）**
1. 添加日志和监控（AOP）
2. 实现错误处理机制
3. 添加健康检查端点
4. 性能优化和限流
5. 安全权限控制

**第四阶段：集成测试（1天）**
1. 与 Claude/Cursor 集成测试
2. 功能测试
3. 性能测试
4. 异常场景测试
5. 文档完善

**预计总工期：5-7 天**

---

### 对比总结

| 维度 | 方案A (LangChain4j) | 方案B (MCP) |
|------|---------------------|-------------|
| **工期** | 4-7天 | 5-7天 |
| **难度** | 中等 | 中等 |
| **代码量** | 较少 | 中等 |
| **标准化** | ❌ 私有方案 | ✅ 行业标准 |
| **生态支持** | ⚠️ 仅限 LangChain4j | ✅ 全面生态 |
| **未来扩展** | ⚠️ 锁定框架 | ✅ 易于扩展 |
| **维护成本** | ⚠️ 中等 | ✅ 低 |

**最终建议**：选择方案B（MCP），虽然工期相近，但长期收益更高。

---

## 六、注意事项

### 6.1 安全考虑
- 所有工具调用必须验证用户身份（Sa-Token）
- 敏感操作需要二次确认或权限控制
- 工具调用频率限制（防止滥用）
- 输入参数严格验证（防止注入攻击）

### 6.2 性能优化
- 合理使用缓存（统计类数据）
- 非阻塞异步执行（I/O密集型操作）
- 工具调用超时控制（防止长时间阻塞）
- 结果压缩传输（减少网络开销）

### 6.3 错误处理
- 工具执行失败友好提示
- 降级策略（工具不可用时）
- 重试机制（网络波动）
- 详细日志记录（便于排查问题）

### 6.4 模型兼容性
- **MCP方案**：支持所有 MCP 兼容的客户端
  - ✅ Claude (Anthropic)
  - ✅ Cursor
  - ✅ VS Code Copilot (扩展)
  - ✅ 其他 MCP 客户端
- **LangChain4j方案**：仅限 LangChain4j 生态
  - ⚠️ GPT-4 / GPT-3.5
  - ⚠️ Claude (通过 LangChain4j)
  - ⚠️ DeepSeek 等兼容模型

### 6.5 MCP 特有注意事项

1. **协议版本**：使用 MCP 0.14.x 版本
2. **传输方式**：推荐 Streamable HTTP（支持流式响应）或 STDIO（本地开发）
3. **认证**：通过 HTTP Header 传递 Sa-Token
4. **部署模式**：可独立部署为 MCP Server，也可内嵌到现有应用
5. **工具发现**：AI 客户端可自动发现可用工具
6. **无需 Spring AI**：直接使用 MCP Java SDK 原生实现

---

## 七、最终方案对比总结

### 三种方案综合对比

| 评估维度 | 方案A<br/>LangChain4j | 方案B<br/>MCP ⭐ | 方案C<br/>手动实现 |
|---------|----------------------|------------------|-------------------|
| **标准化程度** | ⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐ |
| **生态支持** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐ |
| **开发效率** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐ |
| **维护成本** | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐ |
| **代码复杂度** | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| **扩展性** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| **AI客户端兼容** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐ |
| **Spring集成度** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ |
| **学习曲线** | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| **框架依赖** | 绑定 LangChain4j | 无框架依赖 | 无框架依赖 |

### 使用场景推荐

| 场景 | 推荐方案 | 理由 |
|------|---------|------|
| **想要标准化、与AI客户端集成** | 方案B (MCP) | 行业标准，生态完善 |
| **已有 LangChain4j 项目** | 方案A (LangChain4j) | 平滑升级，快速见效 |
| **需要完全自定义控制** | 方案C (手动实现) | 灵活度高，定制能力强 |

---

## 八、结论与建议

### 🎯 最终推荐

> ### **方案B：MCP (Model Context Protocol)**

### 选择理由

1. **标准化是未来**：MCP 已成为 AI 工具调用的行业标准
2. **生态完善**：Anthropic、OpenAI 等背书，MCP Java SDK 官方支持
3. **一次投入，多端收益**：定义一次工具，Claude、Cursor、Copilot 等都能用
4. **社区活跃**：持续更新，文档完善
5. **易于维护**：工具定义清晰，问题排查简单
6. **无框架依赖**：直接使用 MCP Java SDK，不绑定任何框架

### 下一步行动

1. **准备资源**：Maven 依赖（MCP Java SDK）
2. **学习协议**：了解 MCP 协议规范
3. **制定计划**：按阶段计划逐步实施
4. **开始开发**：从核心依赖和工具注册表开始

---

## 附录

### A. 参考资源

- [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)
- [MCP Protocol 规范](https://modelcontextprotocol.io)
- [LangChain4j 官方文档](https://docs.langchain4j.dev)（供参考）

### B. 术语表

| 术语 | 说明 |
|------|------|
| MCP | Model Context Protocol，AI 工具调用标准协议 |
| Tool | AI 可调用的外部工具/函数 |
| Function Calling | 大模型调用外部函数的能力 |
| Spring AI | Spring 官方 AI 集成框架 |
| Sa-Token | 轻量级 Java 权限认证框架 |

### C. 常见问题

**Q: MCP 和 LangChain4j @Tool 能否共存？**
A: 可以。可以在同一项目中同时使用两种方案，但建议统一技术选型。

**Q: MCP Server 能否独立部署？**
A: 可以。MCP 支持 STDIO 传输方式，可以作为独立进程运行。

**Q: 如何保证 MCP 工具调用的安全性？**
A: 通过 Sa-Token 认证、参数验证、权限控制、限流等多层防护。

**Q: 哪些 AI 模型支持 MCP？**
A: Claude、Cursor、VS Code Copilot 扩展、以及所有兼容 MCP 协议的客户端。
