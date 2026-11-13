-- 目的：设备、观影、阅读、衣物的单图关联统一保存于 file.biz_type/biz_id。
-- 前置：PostgreSQL 18+；四张业务表均有 file_id，或均已按当前初始脚本移除。
-- 影响：按原 file_id 回填关联；解除过时关联但不删除文件；共享引用复制文件元数据，不复制存储对象。
-- 重跑：事务执行，异常整体回滚；四列均不存在时只检查/创建约束，不重复回填。
-- 执行：已有库通过独立 Flyway migrate 执行；核对图片关联、行数与业务数据后 validate。
DO $$
DECLARE
  old_columns INTEGER;
BEGIN
  SELECT COUNT(*) INTO old_columns
  FROM information_schema.columns
  WHERE table_schema = current_schema() AND column_name = 'file_id'
    AND table_name IN ('device', 'movie', 'read_record', 'wardrobe_item');
  IF old_columns NOT IN (0, 4) THEN
    RAISE EXCEPTION '图片迁移前结构不完整，要求四个 file_id 均存在或均不存在';
  END IF;
  IF old_columns = 4 THEN
    LOCK TABLE device, movie, read_record, wardrobe_item, file IN ACCESS EXCLUSIVE MODE;
    CREATE TEMP TABLE record_image_refs ON COMMIT DROP AS
      SELECT 'device'::VARCHAR(50) AS biz_type, id AS biz_id, user_id, file_id FROM device
      UNION ALL SELECT 'movie', id, user_id, file_id FROM movie
      UNION ALL SELECT 'read', id, user_id, file_id FROM read_record
      UNION ALL SELECT 'wardrobe_item', id, user_id, file_id FROM wardrobe_item;
    DELETE FROM record_image_refs WHERE NULLIF(BTRIM(file_id), '') IS NULL;
    IF EXISTS (
      SELECT 1 FROM record_image_refs r LEFT JOIN file f ON f.id = r.file_id
      WHERE f.id IS NULL OR f.is_deleted <> 0 OR f.create_user IS DISTINCT FROM r.user_id
        OR f.biz_type <> r.biz_type
        OR (f.biz_id IS NOT NULL AND (f.biz_type, f.biz_id) <> (r.biz_type, r.biz_id))
    ) THEN
      RAISE EXCEPTION '存在缺失、已删除、跨用户、类型错误或被其他记录占用的图片，停止迁移';
    END IF;
    -- 一条文件元数据只关联一个业务记录；历史共享图片保留同一存储对象。
    ALTER TABLE record_image_refs ADD COLUMN target_file_id VARCHAR(32);
    UPDATE record_image_refs r SET target_file_id = x.target_id
    FROM (
      SELECT biz_type, biz_id,
        CASE WHEN ROW_NUMBER() OVER (PARTITION BY file_id ORDER BY biz_type, biz_id) = 1
          THEN file_id ELSE REPLACE(gen_random_uuid()::TEXT, '-', '') END AS target_id
      FROM record_image_refs
    ) x WHERE (r.biz_type, r.biz_id) = (x.biz_type, x.biz_id);
    -- 先解除所有旧关联，避免原有多图或无图片记录在迁移后意外显示旧图片。
    UPDATE file SET biz_id = NULL
    WHERE biz_type IN ('device', 'movie', 'read', 'wardrobe_item') AND biz_id IS NOT NULL;
    INSERT INTO file (id, storage_object_id, file_name, file_size, file_type, hash_value,
                      biz_type, biz_id, is_public, create_user, create_time, update_user, update_time, is_deleted)
      SELECT r.target_file_id, f.storage_object_id, f.file_name, f.file_size, f.file_type, f.hash_value,
             r.biz_type, r.biz_id, f.is_public, f.create_user, f.create_time, f.update_user, f.update_time, f.is_deleted
      FROM record_image_refs r JOIN file f ON f.id = r.file_id
      WHERE r.target_file_id <> r.file_id;
    UPDATE file f SET biz_type = r.biz_type, biz_id = r.biz_id
    FROM record_image_refs r WHERE f.id = r.target_file_id;
    IF EXISTS (
      SELECT 1 FROM record_image_refs r LEFT JOIN file f ON f.id = r.target_file_id
      WHERE f.id IS NULL OR (f.biz_type, f.biz_id) IS DISTINCT FROM (r.biz_type, r.biz_id)
        OR f.create_user IS DISTINCT FROM r.user_id OR f.is_deleted <> 0
    ) THEN
      RAISE EXCEPTION '图片关联迁移核对失败，回滚';
    END IF;
    ALTER TABLE device DROP COLUMN file_id;
    ALTER TABLE movie DROP COLUMN file_id;
    ALTER TABLE read_record DROP COLUMN file_id;
    ALTER TABLE wardrobe_item DROP COLUMN file_id;
  END IF;
END $$;
COMMENT ON COLUMN file.biz_id IS '业务记录ID；上传待绑定或解除关联时为空，设备/观影/阅读/衣物单图由此唯一关联';
-- 已有库迁移与已移除四列的新库初始化得到同一约束。
CREATE UNIQUE INDEX IF NOT EXISTS uk_file_record_image ON file (biz_type, biz_id)
  WHERE is_deleted = 0 AND biz_id IS NOT NULL AND biz_type IN ('device', 'movie', 'read', 'wardrobe_item');
COMMENT ON INDEX uk_file_record_image IS '设备、观影、阅读、衣物每条业务记录至多关联一张有效图片';
