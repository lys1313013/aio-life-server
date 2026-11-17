-- 目的：影视和阅读的开始/完成只按日期记录，与实体 LocalDate、接口 yyyy-MM-dd 及界面日期选择器一致，日期列统一使用 *_date 命名。
-- 前置：PostgreSQL 18+；已执行 V20261117_07；movie、read_record 的 start_time/finish_time 仍为 timestamp(0) without time zone。
-- 影响：重命名四列并改为 date，保留年月日、丢弃时分秒；时分秒从未在接口或界面暴露，豆瓣同步写入的当前时刻属自动补值。不改可空性、默认值、索引、约束或其他列。
-- 重跑：Flyway 成功后不重跑；重复执行会因新列名不存在而失败，已丢弃的时分秒不可恢复。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；失败事务回滚。

ALTER TABLE "movie" RENAME COLUMN "start_time" TO "start_date";
ALTER TABLE "movie" RENAME COLUMN "finish_time" TO "finish_date";
ALTER TABLE "read_record" RENAME COLUMN "start_time" TO "start_date";
ALTER TABLE "read_record" RENAME COLUMN "finish_time" TO "finish_date";

ALTER TABLE "movie" ALTER COLUMN "start_date" TYPE date USING "start_date"::date;
ALTER TABLE "movie" ALTER COLUMN "finish_date" TYPE date USING "finish_date"::date;
ALTER TABLE "read_record" ALTER COLUMN "start_date" TYPE date USING "start_date"::date;
ALTER TABLE "read_record" ALTER COLUMN "finish_date" TYPE date USING "finish_date"::date;

COMMENT ON COLUMN "movie"."start_date" IS '开始观看日期';
COMMENT ON COLUMN "movie"."finish_date" IS '看完日期';
COMMENT ON COLUMN "read_record"."start_date" IS '开始阅读日期';
COMMENT ON COLUMN "read_record"."finish_date" IS '读完日期';
