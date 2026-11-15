-- 目的：用户头像统一保存为 file.biz_type='avatar' 且 file.biz_id=sys_user.id 的单条关联。
-- 前置：PostgreSQL 18+；sys_user 有 avatar_file_id，或已按当前初始脚本移除。
-- 影响：按原 avatar_file_id 回填关联；不绑定失效文件；不删除文件与存储对象。
-- 重跑：事务执行，异常整体回滚；列不存在时只检查/创建约束，不重复回填。
-- 执行：已有库通过独立 Flyway migrate 执行；核对头像关联后 validate。
DO $$
DECLARE
  old_column INTEGER;
BEGIN
  SELECT COUNT(*) INTO old_column
  FROM information_schema.columns
  WHERE table_schema = current_schema() AND table_name = 'sys_user' AND column_name = 'avatar_file_id';
  IF old_column = 1 THEN
    LOCK TABLE sys_user, file IN ACCESS EXCLUSIVE MODE;
    CREATE TEMP TABLE user_avatar_refs ON COMMIT DROP AS
      SELECT id AS user_id, avatar_file_id AS file_id FROM sys_user
      WHERE NULLIF(BTRIM(avatar_file_id), '') IS NOT NULL;
    IF EXISTS (
      SELECT 1 FROM user_avatar_refs r LEFT JOIN file f ON f.id = r.file_id
      WHERE f.id IS NULL OR f.is_deleted <> 0 OR f.create_user IS DISTINCT FROM r.user_id
        OR f.biz_type <> 'avatar'
        OR (f.biz_id IS NOT NULL AND f.biz_id <> r.user_id)
    ) THEN
      RAISE EXCEPTION '存在缺失、已删除、跨用户、类型错误或被其他用户占用的头像文件，停止迁移';
    END IF;
    -- 头像关联以 sys_user 列为准，先解除所有旧关联再回填。
    UPDATE file SET biz_id = NULL WHERE biz_type = 'avatar' AND biz_id IS NOT NULL;
    UPDATE file f SET biz_id = r.user_id FROM user_avatar_refs r WHERE f.id = r.file_id;
    IF EXISTS (
      SELECT 1 FROM user_avatar_refs r LEFT JOIN file f ON f.id = r.file_id
      WHERE f.id IS NULL OR (f.biz_type, f.biz_id) IS DISTINCT FROM ('avatar', r.user_id)
        OR f.create_user IS DISTINCT FROM r.user_id OR f.is_deleted <> 0
    ) THEN
      RAISE EXCEPTION '头像关联迁移核对失败，回滚';
    END IF;
    DROP INDEX IF EXISTS sys_user_idx_user_avatar_file;
    ALTER TABLE sys_user DROP COLUMN avatar_file_id;
  END IF;
END $$;
-- 已有库迁移与已移除列的新库初始化得到同一约束。
CREATE UNIQUE INDEX IF NOT EXISTS uk_file_user_avatar ON file (biz_type, biz_id)
  WHERE is_deleted = 0 AND biz_id IS NOT NULL AND biz_type = 'avatar';
COMMENT ON INDEX uk_file_user_avatar IS '每个用户至多一条有效头像关联';
COMMENT ON COLUMN file.biz_id IS '业务记录ID；上传待绑定或解除关联时为空，设备/观影/阅读/衣物单图及用户头像由此唯一关联';
