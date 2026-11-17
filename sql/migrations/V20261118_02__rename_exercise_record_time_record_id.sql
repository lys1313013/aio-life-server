-- 目的：运动明细的关联列使用被引用对象全名，避免 time_id 被误解为通用时间或时长；接口字段同步为 timeRecordId。
-- 前置：PostgreSQL 18+；已执行 V20261118_01；exercise_record.time_id 仍为 bigint。
-- 影响：只重命名列并更新注释，不改类型、默认值、可空性、数据、索引或约束；索引 exercise_record_idx_user_time 随列自动指向新列，名称按现有用途保留。
-- 重跑：Flyway 成功后不重跑；重复执行会因旧列名不存在而失败。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；失败事务回滚。

ALTER TABLE "exercise_record" RENAME COLUMN "time_id" TO "time_record_id";

COMMENT ON COLUMN "exercise_record"."time_record_id" IS '时迹记录ID（关联time_record.id）';