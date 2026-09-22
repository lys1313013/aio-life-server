# Jev 时迹分类测试用例

日期：2026-09-22。样例全部为虚构数据，不读取真实用户记录，不写数据库。

当前方案：Jev 探针和生产推荐均直接调用 `IWorkCalendarService.isWorkday(date)`、`findPreviousComparableDate(date)`。探针用仓库年度 SQL 数据驱动真实 `WorkCalendarServiceImpl`，数据库和 Redis 为 Mock；不访问真实数据库，不按星期推算。日期类型与参考日不由 Jev 决定。

## 本次验证结论

- 本轮 72 项本地测试通过：38 项 Jev 场景与契约测试、12 项时迹服务回归测试、22 项工作日历服务测试。
- 旧星期方案曾实际执行 6 组 Jev 调用，6/6 与预期一致；详见 [历史实测报告](Jev时迹分类实测报告-2026-09-22.md)。此次已改变参考日期和请求字段，仅重跑本地测试；新的日历请求尚未实测，不能沿用旧命中率。
- 参考日期测试调用了现有生产 `TimeRecordServiceImpl.recommendType` 并验证 Mapper 接收到的日期；数据库本身由 Mock 替代。
- 请求组装、响应验证和 HTTP 调用目前位于 `src/test` 的独立测试探针。它用于确定契约及观察模型效果，**不是已经上线的 TypeSafeClient，也没有把 Jev 接进业务接口**。
- 本地模拟返回的分类来自人为设置，只能证明请求/响应处理符合预期；真实效果请查看单独的实测报告，不能用 Mock 结果充当模型表现。

## 六组业务用例

分类 ID：`101` 洗漱、`102` 早餐、`103` 通勤、`104` 工作、`105` 运动、`106` 休息、`107` 睡觉。

| 用例 | 预测时间 | 今天已录 | 上一个同类日 | 参考日已录 | 人工预期／模拟输出 |
|---|---|---|---|---|---|
| `weekday` | 工作日 09-22 09:00 | 07:30–07:59 早餐；08:00–08:59 通勤 | 工作日 09-21 | 同样早餐和通勤，09:00–11:59 工作 | 工作 `104` |
| `monday` | 工作日 09-21 09:00 | 07:30–07:59 早餐；08:00–08:59 通勤 | 补班日 09-20 | 同样早餐和通勤，09:00–11:59 工作 | 工作 `104` |
| `saturday` | 非工作日 09-26 10:00 | 00:00–08:59 睡觉；09:00–09:29 洗漱；09:30–09:59 早餐 | 假期日 09-25 | 同样睡觉、洗漱和早餐，10:00–10:59 运动 | 运动 `105` |
| `sunday` | 非工作日 09-27 10:00 | 00:00–08:59 睡觉；09:00–09:29 洗漱；09:30–09:59 早餐 | 非工作日 09-26 | 同样睡觉、洗漱和早餐，10:00–10:59 运动 | 运动 `105` |
| `continue-category` | 工作日 09-23 10:00 | 09:00–09:59 工作 | 工作日 09-22 | 09:00–11:59 工作 | 继续工作 `104`，允许和上一分类相同 |
| `empty-reference` | 非工作日 09-26 10:00 | 09:00–09:59 运动 | 假期日 09-25 | 无记录，传空列表 | 运动 `105`，这是人工假设，真实模型可能选其他已有分类 |

表内日期均为 2026 年，类型和参考日期以仓库当前年度日历为准。未来日期只是固定测试时钟场景，不能解读为系统读取了未来真实数据。

为验证过滤，原始样例故意加入干扰记录：非工作日 09-26 的输入中混入补班日 09-20 的工作记录，工作日 09-21 的输入中混入非工作日 09-19 的休息记录，以及今天目标时刻及之后的记录、集合外的分类 ID。发送给 Jev 的请求必须排除这些记录；输入还故意打乱排序，组装后按时间升序。

本轮先测试“今天 + 上一个同类日”的最小输入，未添加设计文档中的 28 天聚合统计。生产 Jev 上下文中的时迹查询、用户归属、真实当前时钟截止、候选分类合并、请求体上限和无历史短路仍属于后续实现；已有日历 Service 直接复用。目标日历缺失会终止探针组装，不发 HTTP；参考日期缺失时传 null 和空记录，不能擅自使用昨天。

## 模拟返回与业务判断

每组合法 Mock 响应采用选中项概率 `1.0`、其他项 `0.0`、置信度 `1.0`；`model` 明确标记为 `mock-only`。这些值是固定测试数据，不是模型效果。

| 返回情况 | 本地测试结果 |
|---|---|
| 选中候选分类，字段和概率合法 | `PREDICTED`，映射为对应分类 ID |
| 选中上一分类且合法 | `PREDICTED`，不强制更换分类 |
| 返回 `unknown`、空串或集合外 ID | `INVALID_RESPONSE`，不接受该分类 |
| 合法分类但置信度 `0.4` | `LOW_CONFIDENCE`，标记需规则降级 |
| 缺答案／缺置信度／概率和不等于 1／多出分类／答案类型错误／ID 返回数字 | `INVALID_RESPONSE` |
| HTTP 401 | 明确报告认证失败，不解析成分类 |

这里验证的是“是否接受结果、是否标记需降级”；尚未将标记接入生产规则回退流程。超时总预算、有限重试、用户权限校验也不在本次探针实现内。

## 运行本地测试

在 `aio-life-server` 目录使用 Java 21：

```bash
mvn -q -Dtest=JevTimeCategoryScenarioTest,JevTimeCategoryLiveTest,TimeRecordServiceImplTest,WorkCalendarServiceImplTest test
```

本机验证时显式选择了已安装的 JDK 21：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  mvn -q -Dtest=JevTimeCategoryScenarioTest,JevTimeCategoryLiveTest,TimeRecordServiceImplTest,WorkCalendarServiceImplTest test
```

运行后，每组用例会导出（采用新目录，不覆盖旧星期方案的实测产物）：

- `target/jev-calendar-scenarios/<id>.request.json`：可直接发送的请求体。
- `target/jev-calendar-scenarios/<id>.mock-result.json`：输入、模拟响应、最终分类及判断状态，含 `mode: MOCK_NOT_REAL_JEV` 标记。
- `target/surefire-reports/`：JUnit 测试报告。

例如查看非工作日的完整输入/模拟输出：`target/jev-calendar-scenarios/saturday.mock-result.json`。

## 运行真实 Jev 测试

先在运行 Maven 的终端环境配置 `AIO_LIFE_TYPESAFE_API_KEY`，不要把密钥写入源码或测试样例。随后显式开启：

```bash
mvn -q -Dtest=JevTimeCategoryLiveTest -Djev.live=true test
```

真实模式会顺序发送上述 6 组虚构数据，产生实际 API 用量。测试探针每组只发一次请求，不自动重试；连接超时 3 秒、读取超时 15 秒是联调配置，不是设计中的生产总预算实现。

结果保存到 `target/jev-calendar-live/<id>.json`，包含：

- `mode: LIVE`、完整样例请求和实际上游响应。
- `expectedCategoryId`、`matchesExpected`：与人工预期是否相符。
- `elapsedMs`、实际 `model`、`usage`、`confidence`。
- `decision`、`acceptedCategoryId`：按设计阈值是否采用预测。

真实测试的断言验证响应契约；与人工预期分类不一致会写入报告，不伪装为通过准确率测试。尤其空参考日样例存在歧义，应结合原始概率和置信度观察，不能以这 6 个例子宣称整体准确率。

## 文件

- `src/test/resources/jev/time-category-scenarios.json`：6 组输入及人工预期。
- `src/test/java/top/aiolife/record/prediction/JevScenarioSupport.java`：独立测试探针，接收 `IWorkCalendarService`。
- `src/test/java/top/aiolife/record/prediction/JevCalendarFixture.java`：以仓库年度日历驱动真实日历 Service，仅模拟数据库和 Redis。
- `src/test/java/top/aiolife/record/prediction/JevTimeCategoryScenarioTest.java`：本地验证。
- `src/test/java/top/aiolife/record/prediction/JevTimeCategoryLiveTest.java`：默认关闭的真实评估入口。
