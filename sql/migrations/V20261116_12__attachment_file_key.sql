-- 目的：新增独立附件对外标识 file_key，内部附件主键与导入结果引用保持 BIGINT。
-- 前置：PostgreSQL 18+，已执行 V20261116_11；停写并备份数据库。
-- 影响：给全部附件（含已删除附件）回填随机 UUIDv4；不改主键、导入结果、业务归属或存储对象。
-- 重跑：Flyway 只执行一次；全部操作在事务中执行，失败整体回滚后可重新 migrate。
-- 执行：独立 Flyway migrate/validate；应用仅接受 file_key，不提供内部数字主键兼容入口。
DO $$
BEGIN
  IF (SELECT data_type FROM information_schema.columns
      WHERE table_schema = current_schema() AND table_name = 'attachment' AND column_name = 'id') IS DISTINCT FROM 'bigint'
      OR (SELECT data_type FROM information_schema.columns
          WHERE table_schema = current_schema() AND table_name = 'import_job' AND column_name = 'result_attachment_id') IS DISTINCT FROM 'bigint' THEN
    RAISE EXCEPTION '附件 file_key 迁移要求内部主键和导入结果引用均为 BIGINT，请检查迁移前结构';
  END IF;
END $$;

ALTER TABLE attachment ADD COLUMN file_key UUID;
UPDATE attachment SET file_key = gen_random_uuid();
ALTER TABLE attachment ALTER COLUMN file_key SET NOT NULL;
CREATE UNIQUE INDEX uk_attachment_file_key ON attachment (file_key);
ALTER TABLE attachment ADD CONSTRAINT ck_attachment_file_key_v4
  CHECK (uuid_extract_version(file_key) IS NOT DISTINCT FROM 4);

COMMENT ON COLUMN attachment.file_key IS '附件对外标识，由应用生成安全随机UUIDv4；接口为带连字符的小写字符串，不代表访问权限';
COMMENT ON COLUMN attachment.id IS '附件内部主键，由应用生成雪花ID；业务API不对外暴露';
COMMENT ON INDEX uk_attachment_file_key IS '附件对外UUID全局唯一，包含已删除附件；用于上传绑定及预览下载定位';
COMMENT ON CONSTRAINT ck_attachment_file_key_v4 ON attachment IS '附件对外标识只允许随机UUIDv4';
