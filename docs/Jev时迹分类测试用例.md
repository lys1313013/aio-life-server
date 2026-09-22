# Jev 时迹分类测试用例

日期：2026-09-22。样例全部为虚构数据，不读取真实用户记录，不写数据库。

当前方案：Jev 探针和生产推荐均直接调用 `IWorkCalendarService.isWorkday(date)`、`findPreviousComparableDate(date)`。探针用仓库年度 SQL 数据驱动真实 `WorkCalendarServiceImpl`，数据库和 Redis 为 Mock；不访问真实数据库，不按星期推算。日期类型与参考日不由 Jev 决定。

## 本次验证结论

- 接入后 93 项后端回归通过：38 项 Jev 场景与契约、7 项生产客户端、5 项上下文组装、13 项时迹服务、22 项工作日历、3 项时迹 Controller、5 项分类覆盖测试。前端相关组件 21 项通过。
- 旧星期方案曾实际执行 6 组 Jev 调用，6/6 与预期一致；详见 [历史实测报告](Jev时迹分类实测报告-2026-09-22.md)。此次已改变参考日期和请求字段，仅重跑本地测试；新的日历请求尚未实测，不能沿用旧命中率。
- 参考日期测试调用了现有生产 `TimeRecordServiceImpl.recommendType` 并验证 Mapper 接收到的日期；数据库本身由 Mock 替代。
- 业务接口现已接入 `JevCategoryRecommendationService` 和 `JevCategoryClient`；请求组装、响应校验已提取为生产 `JevCategoryProtocol`，原场景测试复用同一份契约。代码已接入，尚未部署。
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

生产使用“今天 + 上一个同类日”的最小输入，未添加 28 天聚合统计。时迹查询限定当前用户，并同时按目标分钟与实际当前时钟截止；复用用户可见分类（公共分类及用户覆盖），剔除不可见分类记录；请求体上限 64 KiB，无有效历史时不调用模型。已有日历 Service 直接复用。目标日历缺失会终止探针组装，不发 HTTP；参考日期缺失时传 null 和空记录，不能擅自使用昨天。

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

## 业务接入配置与验证

- `AIO_LIFE_TYPESAFE_API_KEY`：服务端 Key；为空时自动使用现有日历规则。
- `AIO_LIFE_TYPESAFE_ENABLED`：默认 `true`，设为 `false` 可关闭模型调用。
- `AIO_LIFE_TYPESAFE_MODEL`：默认 `jev-latest`。
- 两个入口：`GET /timeRecord/recommendType`、`GET /timeRecord/recommendNext`。
- Jev 总等待上限默认 1000 毫秒，由 `aio.life.server.typesafe.request-timeout-ms` 配置（环境变量 `AIO_LIFE_TYPESAFE_REQUEST_TIMEOUT_MS`）。连接和读取超时也不超过该值；不重试。总等待超时、HTTP 失败、无效响应、置信度低于 0.65、无可用上下文或请求过大时回退现有规则。模型命中直接返回，允许延续上一分类。
- 日历缺失仍返回空分类；工作日/非工作日及最近同类日均由服务端日历决定。
- 不发送用户 ID、记录标题、备注或关联业务详情；仅发送日期、分钟、分类 ID 和候选分类名称。不记录上游响应正文或 Key。
- 本次使用 `test` 账号在本地运行的真实后端完成登录及普通工作日、调休补班日、非工作日、推荐下一时间块接口验证，均返回 `rscode=0`。未新增或修改时迹记录。当前环境无 Key，实际接口验证的是规则回退，不能据此声称 Jev 真实调用已通过。

生产链路回归命令：

```bash
mvn -q -Dtest=JevCategoryRecommendationServiceTest,JevCategoryClientTest,JevTimeCategoryScenarioTest,TimeRecordServiceImplTest,WorkCalendarServiceImplTest,TimeRecordControllerTest,TimeTrackerCategoryServiceImplTest test
```

客户端测试覆盖成功、低置信度、401、超时、损坏 JSON、未配置/关闭及请求过大；上下文测试覆盖用户查询隔离、不可见分类过滤、目标时刻和实际当前时钟截止、无历史短路及参考日期缺失。

协议参考：[TypeSafe 官方 Quick start](https://docs.typesafe.ai/introduction/quickstart)。

## 性能调整（2026-09-22）

- **不缓存、不复用 Jev 结果**。每次有效推荐均重新请求 Jev，便于重复测试。
- 只缓存推荐链路的数据库查询：当天时间块、预测历史记录、可见分类、工作日历、规则兜底查询。按查询类型、用户和查询参数隔离；默认 TTL 5 小时（18000000 毫秒），最多 2000 个查询项。
- 数据缓存配置：`aio.life.server.typesafe.data-cache-ttl-ms` / `AIO_LIFE_TYPESAFE_DATA_CACHE_TTL_MS`，设为 `0` 可关闭。
- 应用内时迹增删改、分类增删改（含管理端）及日历缓存清理后，清理推荐数据缓存；事务提交后再次失效，事务内查询不进入共享缓存。直接改库或其他服务实例的写入最多等待本实例 TTL。
- 同一次 HTTP 请求复用已经验证的用户 ID，减少远程 Redis 查询；每个新请求仍完整鉴权，API Key 临时身份和二级锁检查保留。
- Jev 执行线程上限 8，无等待队列；超时取消等待并直接兜底，迟到响应不覆盖本次结果、不进入缓存。连接层若未立即响应中断，剩余 I/O 仍受连接/读取超时限制。
- 已用用户提供的请求复测：优化前 1886 / 1091 / 1141 毫秒；优化后冷请求 1833 毫秒，随后 802 / 750 / 1026 / 738 毫秒。网络采样仅供本地比较，不代表稳定 SLA。
- 优化后 5 次请求对应 5 次真实 Jev 调用，模型耗时分别为 819 / 382 / 315 / 548 / 307 毫秒；这些请求合计执行 4 次时迹/分类 SQL（不含未开启 SQL 日志的日历查询），证明只复用了数据库数据。
- 无效 Token 实测返回 HTTP 401。1 秒限制针对 Jev 等待，不是整个接口的总耗时上限。
