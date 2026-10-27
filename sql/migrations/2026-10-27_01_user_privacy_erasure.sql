-- 目的：为已有 user 表补齐注册协议记录、账号注销任务字段及扫描索引。
-- 兼容：MySQL 5.7.44、8.0；不依赖 ADD COLUMN IF NOT EXISTS 或 CHECK 约束。
-- 前置：调用方已选择目标库，user 表存在且使用 InnoDB，包含 nickname、is_deleted。
-- 字符集：保留既有表的字符集和排序规则，新增 policy_version 继承表默认值，不转换整表。
-- 权限：需要读取 information_schema、ALTER、CREATE ROUTINE、ALTER ROUTINE、EXECUTE。
-- 顺序：先执行本脚本，再执行 sql/2_ini_data/2026-10-27_privacy_contact.sql，最后启动新版应用。
-- 影响：仅补缺字段和索引，不更新或删除用户数据；既有账号新增字段保持 NULL。
-- 回填：不伪造历史协议同意记录，不把普通逻辑删除账号转成注销清理任务。
-- 重跑：已有定义一致时跳过；同名字段或索引定义冲突时在 ALTER 前报错，禁止并发执行。
-- 执行：使用支持 DELIMITER 的 mysql 客户端，指定 --default-character-set=utf8mb4；遇错停止，不使用 --force。
-- 恢复：DDL 隐式提交，不能靠 ROLLBACK 撤销；失败后核对 SHOW CREATE TABLE user，修正冲突再重跑。
--       _migrate_20261027_user_privacy 为本脚本专用过程；中断后重跑会清理并重新创建，成功后删除。
-- 验证：检查四个字段的类型、可空性、默认值、注释及索引顺序；运行注销待处理查询应不再缺列。

DROP PROCEDURE IF EXISTS `_migrate_20261027_user_privacy`;
DELIMITER $$
CREATE PROCEDURE `_migrate_20261027_user_privacy`()
BEGIN
  DECLARE changes TEXT DEFAULT '';
  DECLARE prerequisite_error VARCHAR(128);

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND TABLE_TYPE = 'BASE TABLE'
  ) THEN
    SET prerequisite_error = CONCAT('Missing base table user in database ', LEFT(COALESCE(DATABASE(), '(none)'), 64));
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = prerequisite_error;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND ENGINE = 'InnoDB'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'user must use InnoDB for transactional account erasure; inspect SHOW CREATE TABLE user';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'nickname') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'user.nickname is missing; check selected database and user table';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'is_deleted') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'user.is_deleted is missing; required by account erasure index';
  END IF;

  -- 先校验所有已有字段，避免部分加列后才发现同名定义冲突。
  IF EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS c
    JOIN (
      SELECT 'policy_version' AS column_name, 'varchar(32)' AS column_type,
        '注册时明确同意的隐私政策与服务条款版本' AS column_comment
      UNION ALL SELECT 'policy_accepted_at', 'datetime', '注册协议同意时间，上海时区'
      UNION ALL SELECT 'erasure_requested_at', 'datetime', '自助注销受理时间，非空表示等待不可撤销的数据清理'
      UNION ALL SELECT 'erasure_retry_at', 'datetime', '注销后台清理下次重试时间，上海时区'
    ) expected ON c.COLUMN_NAME = expected.column_name
    WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'user'
      AND (c.COLUMN_TYPE <> expected.column_type OR c.IS_NULLABLE <> 'YES'
        OR c.COLUMN_DEFAULT IS NOT NULL OR c.EXTRA <> ''
        OR c.COLUMN_COMMENT <> expected.column_comment)
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'privacy/erasure column definition conflict; inspect SHOW CREATE TABLE user';
  END IF;

  -- 非唯一 BTREE 索引按 is_deleted 筛选、erasure_retry_at 到期时间排序，避免全表扫描。
  IF EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND INDEX_NAME = 'idx_user_erasure_retry'
  ) AND (
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND INDEX_NAME = 'idx_user_erasure_retry') <> 2
    OR EXISTS (
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND INDEX_NAME = 'idx_user_erasure_retry'
        AND (NON_UNIQUE <> 1 OR INDEX_TYPE <> 'BTREE' OR SUB_PART IS NOT NULL
          OR COALESCE(COLLATION, '') <> 'A'
          OR NOT ((SEQ_IN_INDEX = 1 AND COALESCE(COLUMN_NAME, '') = 'is_deleted')
            OR (SEQ_IN_INDEX = 2 AND COALESCE(COLUMN_NAME, '') = 'erasure_retry_at')))
    )
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'idx_user_erasure_retry definition conflict; inspect SHOW INDEX FROM user';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'policy_version') THEN
    SET changes = CONCAT(changes,
      'ADD COLUMN `policy_version` VARCHAR(32) DEFAULT NULL COMMENT ''注册时明确同意的隐私政策与服务条款版本'' AFTER `nickname`,');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'policy_accepted_at') THEN
    SET changes = CONCAT(changes,
      'ADD COLUMN `policy_accepted_at` DATETIME DEFAULT NULL COMMENT ''注册协议同意时间，上海时区'' AFTER `policy_version`,');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'erasure_requested_at') THEN
    SET changes = CONCAT(changes,
      'ADD COLUMN `erasure_requested_at` DATETIME DEFAULT NULL COMMENT ''自助注销受理时间，非空表示等待不可撤销的数据清理'' AFTER `policy_accepted_at`,');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'erasure_retry_at') THEN
    SET changes = CONCAT(changes,
      'ADD COLUMN `erasure_retry_at` DATETIME DEFAULT NULL COMMENT ''注销后台清理下次重试时间，上海时区'' AFTER `erasure_requested_at`,');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND INDEX_NAME = 'idx_user_erasure_retry') THEN
    SET changes = CONCAT(changes, 'ADD INDEX `idx_user_erasure_retry` (`is_deleted`, `erasure_retry_at`),');
  END IF;

  -- 合并为一次 ALTER，保留所有既有列和数据；没有缺项则不执行 DDL。
  IF changes <> '' THEN
    SET @aio_privacy_erasure_ddl = CONCAT('ALTER TABLE `user` ', LEFT(changes, CHAR_LENGTH(changes) - 1));
    PREPARE aio_privacy_erasure_stmt FROM @aio_privacy_erasure_ddl;
    EXECUTE aio_privacy_erasure_stmt;
    DEALLOCATE PREPARE aio_privacy_erasure_stmt;
    SET @aio_privacy_erasure_ddl = NULL;
  END IF;
END$$
DELIMITER ;
CALL `_migrate_20261027_user_privacy`();
DROP PROCEDURE `_migrate_20261027_user_privacy`;
