-- 目的：把基线中按 integer 定义的 is_deleted 统一为 smallint，并把 wardrobe_item.file_id 收窄到 varchar(32)。
-- 前置：PostgreSQL 18+，已执行 V20261110_01 及 V20261112_01 至 V20261112_05。
-- 影响：9 张表的 is_deleted 类型变更；wardrobe_item.file_id 长度收窄。迁移前会检查非法删除标记和超长文件ID。
-- 重跑：由 Flyway 版本历史保证只执行一次；失败事务回滚后修正原因再迁移。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；失败事务回滚。
DO $$
DECLARE
  invalid_rows bigint;
BEGIN
  SELECT count(*) INTO invalid_rows FROM (
    SELECT 1 FROM exercise_record WHERE is_deleted NOT IN (0, 1)
    UNION ALL SELECT 1 FROM life_footprint WHERE is_deleted NOT IN (0, 1)
    UNION ALL SELECT 1 FROM milestone WHERE is_deleted NOT IN (0, 1)
    UNION ALL SELECT 1 FROM system_region WHERE is_deleted NOT IN (0, 1)
    UNION ALL SELECT 1 FROM task WHERE is_deleted NOT IN (0, 1)
    UNION ALL SELECT 1 FROM time_tracker_category WHERE is_deleted NOT IN (0, 1)
    UNION ALL SELECT 1 FROM user_body_profile WHERE is_deleted NOT IN (0, 1)
    UNION ALL SELECT 1 FROM user_home_card_preference WHERE is_deleted NOT IN (0, 1)
    UNION ALL SELECT 1 FROM user_menu_hidden WHERE is_deleted NOT IN (0, 1)
  ) invalid;
  IF invalid_rows > 0 THEN
    RAISE EXCEPTION '存在 % 条 is_deleted 不是 0/1 的记录，请先修正后再迁移', invalid_rows;
  END IF;

  SELECT count(*) INTO invalid_rows FROM wardrobe_item
  WHERE file_id IS NOT NULL AND char_length(file_id) > 32;
  IF invalid_rows > 0 THEN
    RAISE EXCEPTION 'wardrobe_item.file_id 存在 % 条超过 32 字符的记录，请先确认后再迁移', invalid_rows;
  END IF;
END
$$;

ALTER TABLE exercise_record ALTER COLUMN is_deleted TYPE smallint USING is_deleted::smallint;
ALTER TABLE life_footprint ALTER COLUMN is_deleted TYPE smallint USING is_deleted::smallint;
ALTER TABLE milestone ALTER COLUMN is_deleted TYPE smallint USING is_deleted::smallint;
ALTER TABLE system_region ALTER COLUMN is_deleted TYPE smallint USING is_deleted::smallint;
ALTER TABLE task ALTER COLUMN is_deleted TYPE smallint USING is_deleted::smallint;
ALTER TABLE time_tracker_category ALTER COLUMN is_deleted TYPE smallint USING is_deleted::smallint;
ALTER TABLE user_body_profile ALTER COLUMN is_deleted TYPE smallint USING is_deleted::smallint;
ALTER TABLE user_home_card_preference ALTER COLUMN is_deleted TYPE smallint USING is_deleted::smallint;
ALTER TABLE user_menu_hidden ALTER COLUMN is_deleted TYPE smallint USING is_deleted::smallint;
ALTER TABLE wardrobe_item ALTER COLUMN file_id TYPE varchar(32);

COMMENT ON COLUMN wardrobe_item.file_id IS '图片文件ID(UUID)';
