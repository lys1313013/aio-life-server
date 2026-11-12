-- 目的：把同名不同单位的时长列改为 <量>_<单位>，并把时迹起止列改为分钟语义的显式名称。
-- 前置：PostgreSQL 18+，已执行 V20261110_01 结构初始化及 V20261112_01 至 V20261112_04 后续适用增量。
-- 影响：重命名 time_record、b_video、performance 的相关列；同步改写 b_video.pages_info 数组内的 duration 键。
--       不改列类型、约束和已有数值；ck_time_record_time_range 随列重命名自动更新引用。
-- 重跑：由 Flyway 版本历史保证只执行一次；目标列不存在或 JSON 非数组时不会静默跳过，修正原因后重新迁移。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；失败事务回滚。

-- 分集信息只处理 jsonb 数组；非数组数据必须先人工确认，避免后续 UPDATE 静默跳过。
DO $$
DECLARE
  invalid_rows bigint;
BEGIN
  SELECT count(*) INTO invalid_rows FROM b_video
  WHERE pages_info IS NOT NULL AND jsonb_typeof(pages_info) <> 'array';
  IF invalid_rows > 0 THEN
    RAISE EXCEPTION 'b_video.pages_info 存在 % 条非数组数据，请先确认后再迁移', invalid_rows;
  END IF;
END
$$;

ALTER TABLE time_record RENAME COLUMN start_time TO start_minute;
ALTER TABLE time_record RENAME COLUMN end_time TO end_minute;
ALTER TABLE time_record RENAME COLUMN duration TO duration_minutes;

ALTER TABLE b_video RENAME COLUMN duration TO duration_seconds;
ALTER TABLE b_video RENAME COLUMN watched_duration TO watched_duration_seconds;

ALTER TABLE performance RENAME COLUMN duration TO duration_minutes;

COMMENT ON COLUMN time_record.start_minute IS '开始分钟（当天 0:00 起的分钟数，0–1439，包含该分钟）';
COMMENT ON COLUMN time_record.end_minute IS '结束分钟（当天 0:00 起的分钟数，0–1439，包含该分钟）';
COMMENT ON COLUMN time_record.duration_minutes IS '时长（分钟），闭区间 end_minute - start_minute + 1，由服务端计算';

COMMENT ON COLUMN b_video.duration_seconds IS '视频总时长（秒）';
COMMENT ON COLUMN b_video.watched_duration_seconds IS '已观看时长（秒）';
COMMENT ON COLUMN b_video.pages_info IS '分集信息：cid-分集ID, page-页码, part-分集标题, durationSeconds-分集时长（秒）';

COMMENT ON COLUMN performance.duration_minutes IS '演出时长（分钟）';

-- 分集元数据是客户端写入的 jsonb 数组；只改写含 duration 键的元素，其他字段和顺序保持不变。
UPDATE b_video
SET pages_info = (
    SELECT jsonb_agg(
        CASE
            WHEN elem ? 'duration'
                THEN (elem - 'duration') || jsonb_build_object('durationSeconds', elem->'duration')
            ELSE elem
        END
        ORDER BY ordinality
    )
    FROM jsonb_array_elements(pages_info) WITH ORDINALITY AS item(elem, ordinality)
)
WHERE pages_info IS NOT NULL
  AND jsonb_typeof(pages_info) = 'array'
  AND EXISTS (
      SELECT 1 FROM jsonb_array_elements(pages_info) AS elem
      WHERE elem ? 'duration'
  );
