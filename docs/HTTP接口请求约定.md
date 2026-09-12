# HTTP 查询与动作接口约定

查询使用 GET，分页和筛选字段通过 URL 参数传递，不发送 GET 请求体。有副作用的操作使用 POST / PUT / DELETE。

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
