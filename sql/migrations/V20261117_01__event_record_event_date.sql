-- 目的：活动记录只保存活动日期，与实体 LocalDate、接口 yyyy-MM-dd 及全库其他日期列一致。
-- 前置：PostgreSQL 18+；已执行 V20261116_12；event_record.event_date 仍为 timestamp(0) without time zone。
-- 影响：把 event_record.event_date 改为 date，已有日期值保留；含非零时分秒的行拒绝执行，需确认可丢弃并手工截断后重试；不回填数据，不改注释、默认值、索引定义与其他列。
-- 重跑：Flyway 成功后不重跑；列已是 date 时手工重复执行不改变结构，已截断的时刻不可恢复。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；失败事务回滚。

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM event_record WHERE event_date <> date_trunc('day', event_date)) THEN
        RAISE EXCEPTION '存在带时分秒的活动日期，确认时刻可丢弃后先截断为日期，再执行本迁移';
    END IF;
END $$;

ALTER TABLE event_record ALTER COLUMN event_date TYPE date USING event_date::date;
