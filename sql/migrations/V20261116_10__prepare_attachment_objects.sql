-- 目的：为附件四表升级增加对象元数据和雪花ID准备列，不删除已有数据。
-- 前置：PostgreSQL 18+，已执行此前适用增量；已有数据环境停写后先迁移至本版本。
-- 影响：新增可空列；非空 file 需执行 tools/attachments/prepare.py 后才能继续下一版本。
-- 重跑：由 Flyway 只执行一次；DDL 事务失败自动回滚，准备工具可重复执行。
-- 执行：独立 Flyway target=20261116.10；空库可直接迁移至最新版本。
-- 部分已有库仍保留封面指针；在执行准备工具前统一回填，避免依赖手工改库。
DO $$ BEGIN
  IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='b_video' AND column_name='cover_file_id') THEN
    EXECUTE $sql$
      INSERT INTO file(id,storage_object_id,file_name,file_size,file_type,hash_value,biz_type,biz_id,is_public,create_user,update_user,is_deleted)
      SELECT md5('b_video_cover:' || v.id::TEXT),f.storage_object_id,f.file_name,f.file_size,f.file_type,f.hash_value,
        'b_video_cover',v.id,0,v.user_id,v.user_id,0
      FROM b_video v JOIN file f ON f.id=v.cover_file_id
      WHERE v.is_deleted=0 AND f.is_deleted=0 AND NOT EXISTS(
        SELECT 1 FROM file existing WHERE existing.biz_type='b_video_cover' AND existing.biz_id=v.id AND existing.is_deleted=0)
    $sql$;
    ALTER TABLE b_video DROP COLUMN cover_file_id;
  END IF;
END $$;
ALTER TABLE file ADD COLUMN upgrade_id BIGINT;
COMMENT ON COLUMN file.upgrade_id IS '附件升级准备的雪花ID，独立准备工具生成并在后续迁移替换UUID';
CREATE UNIQUE INDEX uk_file_upgrade_id ON file(upgrade_id) WHERE upgrade_id IS NOT NULL;
COMMENT ON INDEX uk_file_upgrade_id IS '保证升级准备生成的新附件ID唯一';
ALTER TABLE storage_object ADD COLUMN image_width INTEGER;
ALTER TABLE storage_object ADD COLUMN image_height INTEGER;
COMMENT ON COLUMN storage_object.image_width IS '图片宽度，单位像素；非图片为空';
COMMENT ON COLUMN storage_object.image_height IS '图片高度，单位像素；非图片为空';
