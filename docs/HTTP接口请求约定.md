# HTTP 查询与动作接口约定

查询使用 GET，分页和筛选字段通过 URL 参数传递，不发送 GET 请求体。有副作用的操作使用 POST / PUT / DELETE。

## 微信编程页面数据（2026-10-21）

微信端仅请求 AIO API，由后端访问第三方，避免真机合法域名限制。以下接口要求登录并沿用 GitHub / LeetCode 菜单锁；平台账号和 Token 只从当前用户绑定读取，不接受客户端账号、Token、URL 或 GraphQL 文本：

- `GET /github/calendar`：贡献日历，返回 GitHub GraphQL 包装结构，需要已绑定 GitHub Token。
- `GET /github/repos?page=1`：本人仓库，每页 100 条；页码为 1–100，短页结束。
- `GET /github/repository/{section}?name=repo-name`：本人仓库详情；section 仅支持 `info`、`contributors`，后者最多 100 人。
- `GET /leetcode/data/{section}`：section 仅支持 `profile`、`contest`、`calendar`、`daily`、`recent`，返回对应 GraphQL 的 data 内容。

响应保留 `{ code, message, data }`。第三方 ID 输出字符串，计数保留数字；上游失败返回业务错误供页面重试，不回传上游正文或凭证。CSDN 继续使用 `/csdn/stats`、`/csdn/articles`，抓取失败不再伪装成全零或空列表。先部署后端，再上传小程序；编译通过不能代替目标服务器外网连通性和微信真机验证。

## 本次接口调整（2026-09-12）

以下接口由 POST 改为 GET，路径和响应结构保持不变。旧 POST 请求返回 HTTP 405。

- `GET /b-video/query`
- `GET /device/query`
- `GET /exerciseRecord/query`
- `GET /exerciseRecord/statistics`
- `GET /exerciseRecord/statistics/light`
- `GET /expense/query`
- `GET /expense/statisticsByMonth`
- `GET /expense/statisticsByYear`
- `GET /income/query`
- `GET /income/statisticsByMonth`
- `GET /income/statisticsByYear`
- `GET /memo/query`
- `GET /movie/page`
- `GET /read-record/page`
- `GET /sysDictData/query`
- `GET /sysDictType/query`
- `GET /taskColumn/query`
- `GET /thought/query`
- `GET /timeRecord/query`
- `GET /timeRecord/queryByDateRange`
- `GET /timeRecord/queryByDateRangeForAI`
- `GET /user-center/list`
- `GET /userDictData/admin/query`
- `GET /userDictData/query`

以下动作接口由 GET 改为 POST，旧 GET 请求返回 HTTP 405 且不会触发发送：

- `POST /auth/sendEmailCode`：JSON 请求体 `{"email":"user@example.com"}`。
- `POST /auth/sendResetPasswordCode`：JSON 请求体 `{"email":"user@example.com"}`。
- `POST /leetcode/notifyTodayQuestion`：无需请求体，仍需登录。

现有二级密码重置验证码接口已使用 POST，无需迁移。邮件验证码的业务校验、频率限制保持不变。

## 时迹单日查询（2026-10-04）

`GET /timeRecord/query?date=YYYY-MM-DD` 仅接受单日日期，日期缺失或格式错误返回 HTTP 400。
返回 `data: TimeRecordListVO[]`，当天没有记录时返回 `[]`，不再分页或返回 `items/total`。
Web 首页、日视图和日统计，以及独立移动端已同步适配；前后端须协调发布。

单日结果以登录用户 ID 和日期为缓存键，使用 Redis `timeRecordDay:v1` 缓存，TTL 为 1 小时，空列表也缓存。
新增、修改、单条删除、整日删除在数据库事务成功提交后清理受影响日期；修改日期清理新旧两天。
事务内查询绕过共享缓存，缓存读写故障时查询回源；失效故障记录日志并由 TTL 兜底。
普通注解缓存允许极小概率的并发旧值回填，不保证强一致性。周/月范围查询不增加缓存。

尚未实测目标环境历史单日最大条数和响应体大小；发布前应在目标数据库只读测量。
契约回归使用 151 条模拟记录，验证超过旧分页上限时完整返回，不据此声称真实数据量上限。

## 时迹分类缓存（2026-10-28）

`GET /timeTrackerCategory/list` 和 `/timeTrackerCategory/all` 共用 Redis 原始分类缓存：
`timeTrackerCategory:v2:0` 保存公共分类，`timeTrackerCategory:v2:{userId}` 保存个人分类及覆盖记录。
TTL 为 5 分钟，空列表也缓存；每次请求重新合并公共配置、个人覆盖与父级启停状态，不缓存跨用户共享的合并结果。

读取通过独立缓存 Bean 的 `@Cacheable`，显式使用 Redis `cacheManager`；TTL 和类型化 JSON 序列化集中在 `RedisConfig`。个人新增、修改、删除、隐藏/恢复和排序发布变更事件，由 `@TransactionalEventListener(AFTER_COMMIT)` 配合 `@CacheEvict` 在事务提交后清理本人缓存；公共分类变更清理共享的公共缓存，使所有用户后续读取重新加载公共数据。账号注销也清理个人缓存。回滚不失效，无事务时立即失效，写事务中的查询绕过缓存。

采用标准 Spring Cache 注解，不使用自定义 Lua 回填令牌；并发慢查询仍可能在缓存清理后回填旧值，不保证强一致性，陈旧值由回填后的 5 分钟 TTL 兜底。缓存读取/写入故障时回源数据库，失效故障记录日志；直接 SQL 修改不触发失效，需要同步删除对应缓存键。

## 查询参数

旧请求体 `{"page":2,"pageSize":20,"condition":{"expTypeId":"123","startTime":"2026-09-01 08:30:00"}}`
改为 URL 参数 `page=2&pageSize=20&expTypeId=123&startTime=2026-09-01%2008%3A30%3A00`。

- Web 端 `#/api/query` 的 `getQuery` 和移动端 `@/utils/query` 的 `getQuery` 负责平铺 `condition`、编码参数，原页面传参方式无需修改。
- 数组使用重复参数，例如 `statuses=in_progress&statuses=on_hold`，单元素数组也可正常绑定。
- ID 保留字符串，空值不发送，`false` 和 `0` 保留。日期/日期时间沿用原接口格式。
- 后端 `@QueryParams` 通过应用 ObjectMapper 绑定 DTO，保留 `CommonQuery<T>` 泛型、`@JsonFormat` 日期和枚举 code。参数格式错误返回 HTTP 400。
- 运动统计无筛选参数时，按最近一年查询，仍保留原有条数上限。
- 豆瓣导入预览接收整批记录，继续使用 POST 请求体，避免大型结构化数据放进 URL。

## 重试及发布

Web 端保留现有 GET 网络错误 / 超时 / 5xx 最多重试两次、300/600 毫秒退避的策略；重试移到传输层，避免响应重复解包及重复错误提示。发送验证码的 POST 不进入该重试逻辑。本次不引入写操作自动重试或幂等键。

前后端需要协调发布，并刷新旧页面；独立部署的移动端或其他 HTTP 调用方也需同步升级。MCP 在进程内直接调用控制器方法，其方法签名和调用方式保持不变。
