-- 目的：统一审计字段的默认值与非空约束；收紧系统字典启用状态，并保留 user_dict_data 的 NULL 继承语义。
-- 前置：PostgreSQL 18+，已执行 V20261110_01 至 V20261112_07。
-- 影响：回填 create_time/update_time 的 NULL，并为所有相关列设置默认值和 NOT NULL；
--       sys_dict_type/sys_dict_data.is_enabled 的 NULL 回填为 1 并设为 NOT NULL；
--       user_dict_data.is_enabled 保持可空，新增 0/1/NULL 检查约束。
-- 重跑：由 Flyway 版本历史保证只执行一次；失败事务回滚后修正原因再迁移。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；失败事务回滚。

-- 审计字段：优先用对端时间回填，无法取得时使用当前会话时间；随后统一默认值并收紧非空。
DO $$
DECLARE
  col record;
  has_peer boolean;
BEGIN
  FOR col IN
    SELECT c.table_name, c.column_name
    FROM information_schema.columns c
    JOIN information_schema.tables t
      ON t.table_schema = c.table_schema
     AND t.table_name = c.table_name
     AND t.table_type = 'BASE TABLE'
    WHERE c.table_schema = current_schema()
      AND c.table_name NOT IN ('flyway_schema_history', 'migration_archive')
      AND c.column_name IN ('create_time', 'update_time')
      AND c.data_type LIKE 'timestamp%'
    ORDER BY c.table_name, c.column_name
  LOOP
    SELECT EXISTS (
      SELECT 1
      FROM information_schema.columns peer
      WHERE peer.table_schema = current_schema()
        AND peer.table_name = col.table_name
        AND peer.column_name = CASE
              WHEN col.column_name = 'create_time' THEN 'update_time'
              ELSE 'create_time'
            END
    ) INTO has_peer;

    IF col.column_name = 'create_time' THEN
      IF has_peer THEN
        EXECUTE format(
          'UPDATE %I SET create_time = COALESCE(create_time, update_time, CURRENT_TIMESTAMP) WHERE create_time IS NULL',
          col.table_name);
      ELSE
        EXECUTE format(
          'UPDATE %I SET create_time = COALESCE(create_time, CURRENT_TIMESTAMP) WHERE create_time IS NULL',
          col.table_name);
      END IF;
    ELSE
      IF has_peer THEN
        EXECUTE format(
          'UPDATE %I SET update_time = COALESCE(update_time, create_time, CURRENT_TIMESTAMP) WHERE update_time IS NULL',
          col.table_name);
      ELSE
        EXECUTE format(
          'UPDATE %I SET update_time = COALESCE(update_time, CURRENT_TIMESTAMP) WHERE update_time IS NULL',
          col.table_name);
      END IF;
    END IF;

    EXECUTE format('ALTER TABLE %I ALTER COLUMN %I SET DEFAULT CURRENT_TIMESTAMP', col.table_name, col.column_name);
    EXECUTE format('ALTER TABLE %I ALTER COLUMN %I SET NOT NULL', col.table_name, col.column_name);
  END LOOP;
END
$$;

-- 系统字典的启用状态按 0/1 收紧；个人覆盖记录的 NULL 继续表示继承公共值。
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM sys_dict_type WHERE is_enabled IS NOT NULL AND is_enabled NOT IN (0, 1))
     OR EXISTS (SELECT 1 FROM sys_dict_data WHERE is_enabled IS NOT NULL AND is_enabled NOT IN (0, 1))
     OR EXISTS (SELECT 1 FROM user_dict_data WHERE is_enabled IS NOT NULL AND is_enabled NOT IN (0, 1)) THEN
    RAISE EXCEPTION '字典 is_enabled 存在 0/1 之外的值，请先修正后再迁移';
  END IF;
END
$$;

UPDATE sys_dict_type SET is_enabled = 1 WHERE is_enabled IS NULL;
UPDATE sys_dict_data SET is_enabled = 1 WHERE is_enabled IS NULL;

ALTER TABLE sys_dict_type ALTER COLUMN is_enabled SET DEFAULT 1;
ALTER TABLE sys_dict_type ALTER COLUMN is_enabled SET NOT NULL;
ALTER TABLE sys_dict_data ALTER COLUMN is_enabled SET DEFAULT 1;
ALTER TABLE sys_dict_data ALTER COLUMN is_enabled SET NOT NULL;

ALTER TABLE sys_dict_type ADD CONSTRAINT ck_sys_dict_type_enabled CHECK (is_enabled IN (0, 1));
ALTER TABLE sys_dict_data ADD CONSTRAINT ck_sys_dict_data_enabled CHECK (is_enabled IN (0, 1));
ALTER TABLE user_dict_data ADD CONSTRAINT ck_user_dict_data_enabled CHECK (is_enabled IS NULL OR is_enabled IN (0, 1));

COMMENT ON COLUMN sys_dict_type.is_enabled IS '是否启用：1=启用，0=停用';
COMMENT ON COLUMN sys_dict_data.is_enabled IS '是否启用：1=启用，0=停用';
COMMENT ON COLUMN user_dict_data.is_enabled IS '是否启用：1=启用，0=停用；NULL表示未设置，个人覆盖项继承基础值';
