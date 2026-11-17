-- 目的：客户端来源 IP 统一使用 client_ip 列名与 varchar(45)，IPv6 文本最长 45 字符；服务器实例 IP 保持 server_ip 并同样收窄。
-- 前置：PostgreSQL 18+；已执行 V20261117_08；login_log、mail_log、sys_activity_log 仍为 ip_address，data_export_audit_event.client_ip 为 varchar(128)，sys_activity_log.server_ip 为 varchar(64)。
-- 影响：重命名三表列并更新注释，收窄三列长度；不改可空性、默认值、索引、数据与其他列。
-- 重跑：Flyway 成功后不重跑；重复执行会因新列名不存在而失败。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；失败事务回滚。
-- 校验：收窄长度时已有值超过 45 字符会整体拒绝执行，先核对实际数据再迁移。

ALTER TABLE "login_log" RENAME COLUMN "ip_address" TO "client_ip";
ALTER TABLE "mail_log" RENAME COLUMN "ip_address" TO "client_ip";
ALTER TABLE "sys_activity_log" RENAME COLUMN "ip_address" TO "client_ip";

ALTER TABLE "sys_activity_log" ALTER COLUMN "client_ip" TYPE varchar(45);
ALTER TABLE "sys_activity_log" ALTER COLUMN "server_ip" TYPE varchar(45);
ALTER TABLE "data_export_audit_event" ALTER COLUMN "client_ip" TYPE varchar(45);

COMMENT ON COLUMN "login_log"."client_ip" IS '客户端来源IP，IPv4或IPv6';
COMMENT ON COLUMN "mail_log"."client_ip" IS '请求客户端IP，IPv4或IPv6';
COMMENT ON COLUMN "sys_activity_log"."client_ip" IS '客户端来源IP，IPv4或IPv6';
COMMENT ON COLUMN "sys_activity_log"."server_ip" IS '处理请求的后端实例IPv4或IPv6';
COMMENT ON COLUMN "data_export_audit_event"."client_ip" IS '可信代理处理后的客户端来源IP，后台任务为空';
