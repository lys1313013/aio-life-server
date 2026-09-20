# Redisson 通知互斥

使用 `org.redisson:redisson:3.52.0` 核心客户端，复用 Spring Boot 的 Redis 连接信息，保留现有 Lettuce、缓存和 Sa-Token 配置。当前支持项目使用的单节点 Redis；包括数据库编号、认证、TLS 和连接超时。集群/哨兵配置会明确报错，需要先扩展对应的 Redisson 连接模式。

## 锁的范围

- LeetCode 未刷检查：`leetcode:check:lock`。
- LeetCode 每日一题：`leetcode:daily:lock`，定时和手动触发共享业务入口。
- 邮件、站内信：按通知类型、日期事件、用户和渠道加锁，锁内检查成功标记，发送成功后保留标记两天。发送失败或跳过不写成功标记，下一次触发允许再尝试；各渠道独立处理。
- 飞书入队：按用户和业务事件保护查询、插入。已有 JDBC 事务时，锁延迟到提交或回滚后释放；只使用普通 SELECT：先识别本事务已有入队；若查不到，临时挂起外层事务核对最新已提交记录，再恢复原事务插入。避免 REPEATABLE READ 旧快照造成重复入队，不使用显式数据库锁。
- 飞书重试：`notification:feishu:retry:redisson-lock`，互斥覆盖整次扫描和投递。

所有锁使用非阻塞 `tryLock()`，竞争失败立即跳过，不排队逐个执行。不指定固定 leaseTime，由 watchdog 续期。异常路径释放当前线程仍持有的锁。机制参考 [Redisson 官方锁文档](https://redisson.pro/docs/data-and-services/locks-and-synchronizers/#lock)。

## 部署和边界

事务内去重核对会短暂使用额外数据库连接；连接池需为这种读取预留容量。

无需 SQL 迁移。所有实例必须共享同一个 Redis 数据库、MySQL 和业务时区。升级时避免新旧版本同时运行通知任务：旧版本没有 LeetCode 锁，飞书旧锁是字符串、新锁是 Redisson hash，使用不同 key 防止类型冲突，但两者不互斥。

成功标记解决锁释放后晚到实例再次执行的问题，不能把外部邮件/飞书发送和 Redis、数据库写入变成一个原子操作。进程在外部发送成功后、结果记录前退出，或 Redis 数据丢失、锁续期失败，仍存在重复投递窗口。发送失败不会新增自动重试任务，沿用原有触发机制。

上线前已重复入队的历史记录不会自动清理。升级当天此前已发出的邮件和站内信没有成功标记，再次触发可能再发一次。反馈状态通知、验证码限流逻辑不在本次修改范围。

## 测试

单元测试：配置映射、Lettuce 共存、锁竞争、异常释放、锁所有权、事务结束释放、发送成功/失败返回值、成功标记和 LeetCode 业务入口。

集成测试：两个独立 Redisson 客户端模拟多实例；验证 16 路通知发送/飞书入队、晚到请求、不同渠道和日期事件、失败后再次触发、真实 watchdog 续期、重试任务互斥、MySQL 提交/回滚、旧快照、同一事务重复调用和不同事件并发事务。

通过 `AIO_LOCK_TEST_REDIS_PORT` 指定本机临时 Redis 端口；通过 `AIO_LOCK_TEST_MYSQL_URL` 指定专用临时 MySQL 数据库（测试账号 root，密码 notification_test）。MySQL 测试会重建 `notification_delivery`，只能指向临时库。未设置对应环境变量时跳过依赖它的测试。不会发送真实邮件或飞书消息。

```bash
mvn -Dtest=DistributedLockExecutorTest,RedissonConfigTest,NotificationSenderResultTest,NotificationSendGuardTest,RedissonNotificationIntegrationTest,FeishuEnqueueLockIntegrationTest,LeetcodeServiceImplTest test
```
