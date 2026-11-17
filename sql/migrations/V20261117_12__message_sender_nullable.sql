-- 目的：系统通知没有发送用户，允许 message.sender_id 为空，并约束发送人与消息类型一致。
-- 前置：PostgreSQL 18+；已执行 V20261117_11；执行前停写并备份数据库。
-- 影响：把已有系统通知（type=0）的 sender_id 清空；type=1 缺少 sender_id 时迁移整体失败，需先人工核对。
-- 重跑：Flyway 只执行一次；数据修正与 DDL 在同一事务执行，失败整体回滚，修正后对同一版本重新执行。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；遇错停止。

SET LOCAL lock_timeout = '10s';

ALTER TABLE message ALTER COLUMN sender_id DROP NOT NULL;

UPDATE message
   SET sender_id = NULL
 WHERE type = 0
   AND sender_id IS NOT NULL;

COMMENT ON COLUMN message.sender_id IS '发送用户ID；系统通知（type=0）为空，用户消息（type=1）必填';

ALTER TABLE message ADD CONSTRAINT ck_message_sender CHECK (
    (type = 0 AND sender_id IS NULL) OR (type = 1 AND sender_id IS NOT NULL)
);
COMMENT ON CONSTRAINT ck_message_sender ON message IS '系统通知没有发送用户，用户消息必须有发送用户';