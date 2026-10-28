-- 目的：对齐收支字段、用户字典默认值与查询索引，删除已取消的敏感日志字段。
-- 兼容：MySQL 5.7.44 / 8.0；不依赖 CHECK 或 ADD COLUMN IF NOT EXISTS。
-- 前置：调用方选择目标库；expense、income、user_dict_data、user_dict_type、login_log、mail_log
--       均已存在且为 InnoDB。停止相关应用写入、任务和并发迁移，核对运行版本不再读写
--       login_log.password / mail_log.content；先备份需要保留的业务数据并确认敏感旧值清理范围。
-- 权限：SELECT、ALTER、CREATE ROUTINE、ALTER ROUTINE、EXECUTE、CREATE TEMPORARY TABLES，
--       以及查看相关表、索引、外键、CHECK、视图、存储过程和触发器定义的权限。
-- 顺序：同日 00、01、02 中适用的增量之后执行；新库也可执行，已符合定义的项目跳过。
-- 数据：类型字段只接受无空白的十进制整数字符串（可有负号及前导零），完整检查有符号 BIGINT
--       范围后转换；NULL 归属/金额由人工核实修复，本脚本不回填、不删除业务记录。
--       user_dict_type 只去掉 id 自增和 dict_name/dict_type 的空字符串默认值，保留现有值。
-- 删除：DROP COLUMN 会永久删除两列中的历史密码/邮件正文；不读取、输出、复制这些字段内容。
--       无法用 ROLLBACK 恢复删除内容；恢复只能使用事先按保留策略管理的备份。
-- 索引：仅补齐三项非唯一查询索引；保留已有其他索引、sys_dict_type 唯一约束和排序规则。
-- 重跑：全部前置检查通过后才修改业务表；当前定义跳过，同名冲突和未知定义停止。
--       DDL 隐式提交，后续 ALTER 失败时已成功项目保留；保持停写，核查原因后重跑整个文件。
-- 执行：mysql --default-character-set=utf8mb4 <本文件；指定目标库、遇错停止，禁止 --force。
-- 边界：不处理文章表、其他历史字段、取消业务表、备份表或整表字符集转换。

-- 过程固定使用严格模式；创建后立即恢复调用连接原模式，成功/异常调用均不改变连接模式。
SET @aio_align_saved_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION';
DROP PROCEDURE IF EXISTS `_migrate_20261028_schema_alignment`;
DELIMITER $$
CREATE PROCEDURE `_migrate_20261028_schema_alignment`()
BEGIN
  DECLARE problem VARCHAR(128);
  DECLARE done BOOLEAN DEFAULT FALSE;
  DECLARE prepared BOOLEAN DEFAULT FALSE;
  DECLARE table_value VARCHAR(64);
  DECLARE column_value VARCHAR(64);
  DECLARE kind_value VARCHAR(16);
  DECLARE type_value VARCHAR(255);
  DECLARE nullable_value VARCHAR(3);
  DECLARE default_value TEXT;
  DECLARE extra_value VARCHAR(255);
  DECLARE charset_value VARCHAR(64);
  DECLARE collation_value VARCHAR(64);
  DECLARE comment_value TEXT;
  DECLARE target_comment VARCHAR(255);
  DECLARE changes LONGTEXT DEFAULT '';
  DECLARE current_table VARCHAR(64) DEFAULT '';
  DECLARE definition_value LONGTEXT;
  DECLARE magnitude_expression TEXT;
  DECLARE targets CURSOR FOR
    SELECT p.table_name, p.column_name, p.change_kind, c.COLUMN_TYPE, c.IS_NULLABLE,
      c.COLUMN_DEFAULT, c.EXTRA, c.CHARACTER_SET_NAME, c.COLLATION_NAME,
      c.COLUMN_COMMENT, p.target_comment
    FROM `_aio_align_columns` p
    JOIN information_schema.COLUMNS c ON c.TABLE_SCHEMA = DATABASE()
      AND c.TABLE_NAME = p.table_name AND c.COLUMN_NAME = p.column_name
    ORDER BY p.table_name, p.column_name;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    IF prepared THEN DEALLOCATE PREPARE aio_align_stmt; END IF;
    DROP TEMPORARY TABLE IF EXISTS `_aio_align_columns`;
    DROP TEMPORARY TABLE IF EXISTS `_aio_align_indexes`;
    SET @aio_align_sql = NULL;
    SET @aio_align_invalid = NULL;
    RESIGNAL;
  END;

  DROP TEMPORARY TABLE IF EXISTS `_aio_align_columns`;
  CREATE TEMPORARY TABLE `_aio_align_columns` (
    `table_name` VARCHAR(64) NOT NULL COMMENT '本次迁移的目标表',
    `column_name` VARCHAR(64) NOT NULL COMMENT '本次迁移的目标字段',
    `change_kind` VARCHAR(16) NOT NULL COMMENT '字段调整种类',
    `target_comment` VARCHAR(255) NOT NULL COMMENT '调整后的中文字段注释',
    PRIMARY KEY (`table_name`, `column_name`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='本次结构对齐的临时字段清单';
  INSERT INTO `_aio_align_columns` VALUES
    ('expense', 'exp_type_id', 'INTEGER', '支出类型ID'),
    ('expense', 'user_id', 'OWNER', '用户id'),
    ('income', 'inc_type_id', 'INTEGER', '收入类型'),
    ('income', 'amt', 'AMOUNT', '收入'),
    ('user_dict_type', 'id', 'ASSIGNED_ID', '主键ID'),
    ('user_dict_type', 'dict_name', 'NULL_DEFAULT', '字典名称'),
    ('user_dict_type', 'dict_type', 'NULL_DEFAULT', '字典类型');

  DROP TEMPORARY TABLE IF EXISTS `_aio_align_indexes`;
  CREATE TEMPORARY TABLE `_aio_align_indexes` (
    `table_name` VARCHAR(64) NOT NULL COMMENT '索引所属表',
    `index_name` VARCHAR(64) NOT NULL COMMENT '目标索引名称',
    `sequence_number` INT NOT NULL COMMENT '字段在索引中的顺序，从1开始',
    `column_name` VARCHAR(64) NOT NULL COMMENT '索引字段名称',
    PRIMARY KEY (`table_name`, `index_name`, `sequence_number`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='本次补齐的非唯一查询索引';
  -- 用户筛选后按字典类型查询；按模板读取覆盖项，保留其他历史索引。
  INSERT INTO `_aio_align_indexes` VALUES
    ('user_dict_data', 'idx_user_dict_type', 1, 'user_id'),
    ('user_dict_data', 'idx_user_dict_type', 2, 'dict_type'),
    ('user_dict_data', 'idx_user_dict_template', 1, 'template_id'),
    ('user_dict_type', 'idx_user_dict_type_user', 1, 'user_id'),
    ('user_dict_type', 'idx_user_dict_type_user', 2, 'dict_type');

  -- 所有表、字段、索引和废弃列依赖的预检均在首条业务 ALTER 之前完成。
  SELECT MIN(e.table_name) INTO problem FROM (
    SELECT 'expense' table_name UNION ALL SELECT 'income' UNION ALL SELECT 'user_dict_data'
    UNION ALL SELECT 'user_dict_type' UNION ALL SELECT 'login_log' UNION ALL SELECT 'mail_log'
  ) e LEFT JOIN information_schema.TABLES t ON t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = e.table_name
  WHERE t.TABLE_NAME IS NULL OR t.TABLE_TYPE <> 'BASE TABLE' OR t.ENGINE <> 'InnoDB';
  IF problem IS NOT NULL THEN
    SET problem = CONCAT('Missing or non-InnoDB base table: ', problem);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = problem;
  END IF;
  SELECT MIN(CONCAT(p.table_name, '.', p.column_name)) INTO problem
  FROM `_aio_align_columns` p LEFT JOIN information_schema.COLUMNS c
    ON c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = p.table_name AND c.COLUMN_NAME = p.column_name
  WHERE c.COLUMN_NAME IS NULL OR COALESCE(c.GENERATION_EXPRESSION, '') <> ''
    OR (p.change_kind = 'INTEGER' AND (c.DATA_TYPE NOT IN ('bigint', 'mediumtext')
      OR c.COLUMN_TYPE LIKE '%unsigned%' OR c.COLUMN_TYPE LIKE '%zerofill%' OR c.EXTRA <> ''
      OR c.COLUMN_DEFAULT IS NOT NULL))
    OR (p.change_kind = 'OWNER' AND (c.DATA_TYPE <> 'bigint' OR c.COLUMN_TYPE LIKE '%unsigned%'
      OR c.COLUMN_TYPE LIKE '%zerofill%' OR c.EXTRA <> '' OR c.COLUMN_DEFAULT IS NOT NULL))
    OR (p.change_kind = 'AMOUNT' AND (c.COLUMN_TYPE <> 'decimal(10,2)'
      OR c.EXTRA <> '' OR c.COLUMN_DEFAULT IS NOT NULL))
    OR (p.change_kind = 'ASSIGNED_ID' AND (c.DATA_TYPE <> 'bigint' OR c.COLUMN_TYPE LIKE '%unsigned%'
      OR c.COLUMN_TYPE LIKE '%zerofill%' OR c.EXTRA NOT IN ('', 'auto_increment')
      OR c.IS_NULLABLE <> 'NO' OR c.COLUMN_DEFAULT IS NOT NULL))
    OR (p.change_kind = 'NULL_DEFAULT' AND (c.COLUMN_TYPE <> 'varchar(100)' OR c.IS_NULLABLE <> 'YES'
      OR c.EXTRA <> '' OR (c.COLUMN_DEFAULT IS NOT NULL AND c.COLUMN_DEFAULT <> '')));
  IF problem IS NOT NULL THEN
    SET problem = CONCAT('Column definition conflict: ', problem, '; inspect SHOW CREATE TABLE');
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = problem;
  END IF;

  SELECT MIN(CONCAT(p.table_name, '.', p.column_name)) INTO problem
  FROM `_aio_align_indexes` p LEFT JOIN information_schema.COLUMNS c
    ON c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = p.table_name AND c.COLUMN_NAME = p.column_name
  WHERE c.COLUMN_NAME IS NULL OR COALESCE(c.GENERATION_EXPRESSION, '') <> ''
    OR (p.column_name IN ('user_id', 'template_id') AND (c.DATA_TYPE <> 'bigint'
      OR c.COLUMN_TYPE LIKE '%unsigned%' OR c.COLUMN_TYPE LIKE '%zerofill%'))
    OR (p.column_name = 'dict_type' AND c.COLUMN_TYPE <> 'varchar(100)');
  IF problem IS NOT NULL THEN
    SET problem = CONCAT('Index source column conflict: ', problem);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = problem;
  END IF;
  SELECT MIN(CONCAT(s.TABLE_NAME, '.', s.INDEX_NAME)) INTO problem
  FROM information_schema.STATISTICS s
  LEFT JOIN `_aio_align_indexes` p ON p.table_name = s.TABLE_NAME
    AND p.index_name = s.INDEX_NAME AND p.sequence_number = s.SEQ_IN_INDEX
  WHERE s.TABLE_SCHEMA = DATABASE()
    AND ((s.TABLE_NAME = 'user_dict_data' AND s.INDEX_NAME IN ('idx_user_dict_type', 'idx_user_dict_template'))
      OR (s.TABLE_NAME = 'user_dict_type' AND s.INDEX_NAME = 'idx_user_dict_type_user'))
    AND (p.column_name IS NULL
    OR NOT (s.COLUMN_NAME <=> p.column_name) OR s.NON_UNIQUE <> 1 OR s.INDEX_TYPE <> 'BTREE'
    OR s.SUB_PART IS NOT NULL OR COALESCE(s.COLLATION, '') <> 'A');
  IF problem IS NOT NULL THEN
    SET problem = CONCAT('Index definition conflict: ', problem);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = problem;
  END IF;
  SELECT MIN(CONCAT(p.table_name, '.', p.index_name)) INTO problem
  FROM `_aio_align_indexes` p
  WHERE EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE()
    AND s.TABLE_NAME = p.table_name AND s.INDEX_NAME = p.index_name)
    AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE()
      AND s.TABLE_NAME = p.table_name AND s.INDEX_NAME = p.index_name AND s.SEQ_IN_INDEX = p.sequence_number);
  IF problem IS NOT NULL THEN
    SET problem = CONCAT('Incomplete index definition: ', problem);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = problem;
  END IF;
  -- MySQL 8 可见性是查询索引契约的一部分；5.7 没有该元数据列。
  IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'information_schema'
      AND TABLE_NAME = 'STATISTICS' AND COLUMN_NAME = 'IS_VISIBLE') THEN
    SET @aio_align_sql = 'SELECT EXISTS(SELECT 1 FROM information_schema.STATISTICS s JOIN `_aio_align_indexes` p ON p.table_name=s.TABLE_NAME AND p.index_name=s.INDEX_NAME WHERE s.TABLE_SCHEMA=DATABASE() AND s.IS_VISIBLE<>''YES'') INTO @aio_align_invalid';
    PREPARE aio_align_stmt FROM @aio_align_sql; SET prepared = TRUE;
    EXECUTE aio_align_stmt; DEALLOCATE PREPARE aio_align_stmt; SET prepared = FALSE;
    IF @aio_align_invalid THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Target dictionary index is invisible; inspect SHOW INDEX';
    END IF;
  END IF;

  -- MEDIUMTEXT 转 BIGINT 不能自动处理前缀/FULLTEXT/函数索引或依赖约束；先检查两张表，
  -- 避免第一张已转换后，第二张才因依赖而失败。已为 BIGINT 时保留其既有索引。
  IF EXISTS (
    SELECT 1 FROM information_schema.COLUMNS c
    JOIN information_schema.STATISTICS s ON s.TABLE_SCHEMA = c.TABLE_SCHEMA
      AND s.TABLE_NAME = c.TABLE_NAME AND s.COLUMN_NAME = c.COLUMN_NAME
    WHERE c.TABLE_SCHEMA = DATABASE() AND c.DATA_TYPE = 'mediumtext'
      AND ((c.TABLE_NAME = 'expense' AND c.COLUMN_NAME = 'exp_type_id')
        OR (c.TABLE_NAME = 'income' AND c.COLUMN_NAME = 'inc_type_id'))
  ) OR EXISTS (
    SELECT 1 FROM information_schema.COLUMNS c
    WHERE c.TABLE_SCHEMA = DATABASE() AND c.DATA_TYPE = 'mediumtext'
      AND ((c.TABLE_NAME = 'expense' AND c.COLUMN_NAME = 'exp_type_id')
        OR (c.TABLE_NAME = 'income' AND c.COLUMN_NAME = 'inc_type_id'))
      AND EXISTS (SELECT 1 FROM information_schema.KEY_COLUMN_USAGE k
        WHERE (k.TABLE_SCHEMA = c.TABLE_SCHEMA AND k.TABLE_NAME = c.TABLE_NAME AND k.COLUMN_NAME = c.COLUMN_NAME)
          OR (k.REFERENCED_TABLE_SCHEMA = c.TABLE_SCHEMA AND k.REFERENCED_TABLE_NAME = c.TABLE_NAME
            AND k.REFERENCED_COLUMN_NAME = c.COLUMN_NAME))
  ) OR EXISTS (
    SELECT 1 FROM information_schema.COLUMNS c
    JOIN information_schema.COLUMNS dependency ON dependency.TABLE_SCHEMA = c.TABLE_SCHEMA
      AND dependency.TABLE_NAME = c.TABLE_NAME
    WHERE c.TABLE_SCHEMA = DATABASE() AND c.DATA_TYPE = 'mediumtext'
      AND ((c.TABLE_NAME = 'expense' AND c.COLUMN_NAME = 'exp_type_id')
        OR (c.TABLE_NAME = 'income' AND c.COLUMN_NAME = 'inc_type_id'))
      AND dependency.GENERATION_EXPRESSION REGEXP CONCAT('(^|[^a-zA-Z0-9_])', c.COLUMN_NAME, '([^a-zA-Z0-9_]|$)')
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Financial text type column has index/constraint/generated dependency; resolve before migration';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'information_schema'
      AND TABLE_NAME = 'STATISTICS' AND COLUMN_NAME = 'EXPRESSION') THEN
    SET @aio_align_sql = 'SELECT EXISTS(SELECT 1 FROM information_schema.COLUMNS c JOIN information_schema.STATISTICS s ON s.TABLE_SCHEMA=c.TABLE_SCHEMA AND s.TABLE_NAME=c.TABLE_NAME WHERE c.TABLE_SCHEMA=DATABASE() AND c.DATA_TYPE=''mediumtext'' AND ((c.TABLE_NAME=''expense'' AND c.COLUMN_NAME=''exp_type_id'') OR (c.TABLE_NAME=''income'' AND c.COLUMN_NAME=''inc_type_id'')) AND s.EXPRESSION REGEXP CONCAT(''(^|[^a-zA-Z0-9_])'',c.COLUMN_NAME,''([^a-zA-Z0-9_]|$)'')) INTO @aio_align_invalid';
    PREPARE aio_align_stmt FROM @aio_align_sql; SET prepared = TRUE;
    EXECUTE aio_align_stmt; DEALLOCATE PREPARE aio_align_stmt; SET prepared = FALSE;
    IF @aio_align_invalid THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Financial text type column has functional index dependency; resolve before migration';
    END IF;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'information_schema'
      AND TABLE_NAME = 'CHECK_CONSTRAINTS') THEN
    SET @aio_align_sql = 'SELECT EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS t JOIN information_schema.CHECK_CONSTRAINTS k ON k.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME=t.CONSTRAINT_NAME JOIN information_schema.COLUMNS c ON c.TABLE_SCHEMA=t.TABLE_SCHEMA AND c.TABLE_NAME=t.TABLE_NAME WHERE c.TABLE_SCHEMA=DATABASE() AND c.DATA_TYPE=''mediumtext'' AND ((c.TABLE_NAME=''expense'' AND c.COLUMN_NAME=''exp_type_id'') OR (c.TABLE_NAME=''income'' AND c.COLUMN_NAME=''inc_type_id'')) AND k.CHECK_CLAUSE REGEXP CONCAT(''(^|[^a-zA-Z0-9_])'',c.COLUMN_NAME,''([^a-zA-Z0-9_]|$)'')) INTO @aio_align_invalid';
    PREPARE aio_align_stmt FROM @aio_align_sql; SET prepared = TRUE;
    EXECUTE aio_align_stmt; DEALLOCATE PREPARE aio_align_stmt; SET prepared = FALSE;
    IF @aio_align_invalid THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Financial text type column has CHECK dependency; resolve before migration';
    END IF;
  END IF;

  IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND ((TABLE_NAME = 'login_log' AND COLUMN_NAME = 'password')
      OR (TABLE_NAME = 'mail_log' AND COLUMN_NAME = 'content'))
    AND ((TABLE_NAME = 'login_log' AND COLUMN_TYPE <> 'varchar(255)')
      OR (TABLE_NAME = 'mail_log' AND DATA_TYPE <> 'text') OR IS_NULLABLE <> 'YES'
      OR COLUMN_DEFAULT IS NOT NULL OR EXTRA <> '' OR COALESCE(GENERATION_EXPRESSION, '') <> '')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Legacy log column definition conflict; inspect SHOW CREATE TABLE';
  END IF;
  -- 索引、外键、生成列、CHECK、触发器、存储过程和视图依赖需人工处理，不能随 DROP COLUMN 隐式丢失。
  IF EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
      AND ((TABLE_NAME = 'login_log' AND COLUMN_NAME = 'password') OR (TABLE_NAME = 'mail_log' AND COLUMN_NAME = 'content')))
    OR EXISTS (SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
      WHERE (TABLE_SCHEMA = DATABASE() AND ((TABLE_NAME = 'login_log' AND COLUMN_NAME = 'password')
        OR (TABLE_NAME = 'mail_log' AND COLUMN_NAME = 'content')))
      OR (REFERENCED_TABLE_SCHEMA = DATABASE() AND ((REFERENCED_TABLE_NAME = 'login_log' AND REFERENCED_COLUMN_NAME = 'password')
        OR (REFERENCED_TABLE_NAME = 'mail_log' AND REFERENCED_COLUMN_NAME = 'content'))))
    OR EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
      AND ((TABLE_NAME = 'login_log' AND GENERATION_EXPRESSION REGEXP '(^|[^a-zA-Z0-9_])password([^a-zA-Z0-9_]|$)')
        OR (TABLE_NAME = 'mail_log' AND GENERATION_EXPRESSION REGEXP '(^|[^a-zA-Z0-9_])content([^a-zA-Z0-9_]|$)')))
    OR EXISTS (SELECT 1 FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE()
      AND (((EVENT_OBJECT_TABLE = 'login_log' OR ACTION_STATEMENT LIKE '%login_log%') AND ACTION_STATEMENT REGEXP '(^|[^a-zA-Z0-9_])password([^a-zA-Z0-9_]|$)')
        OR ((EVENT_OBJECT_TABLE = 'mail_log' OR ACTION_STATEMENT LIKE '%mail_log%') AND ACTION_STATEMENT REGEXP '(^|[^a-zA-Z0-9_])content([^a-zA-Z0-9_]|$)')))
    OR EXISTS (SELECT 1 FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = DATABASE()
      AND ROUTINE_NAME <> '_migrate_20261028_schema_alignment'
      AND ((ROUTINE_DEFINITION LIKE '%login_log%' AND ROUTINE_DEFINITION REGEXP '(^|[^a-zA-Z0-9_])password([^a-zA-Z0-9_]|$)')
        OR (ROUTINE_DEFINITION LIKE '%mail_log%' AND ROUTINE_DEFINITION REGEXP '(^|[^a-zA-Z0-9_])content([^a-zA-Z0-9_]|$)')))
    OR EXISTS (SELECT 1 FROM information_schema.VIEWS WHERE TABLE_SCHEMA = DATABASE()
      AND ((VIEW_DEFINITION LIKE '%login_log%' AND VIEW_DEFINITION REGEXP '(^|[^a-zA-Z0-9_])password([^a-zA-Z0-9_]|$)')
        OR (VIEW_DEFINITION LIKE '%mail_log%' AND VIEW_DEFINITION REGEXP '(^|[^a-zA-Z0-9_])content([^a-zA-Z0-9_]|$)'))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Legacy log column has schema dependencies; resolve explicitly before migration';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'information_schema'
      AND TABLE_NAME = 'CHECK_CONSTRAINTS') THEN
    SET @aio_align_sql = 'SELECT EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS t JOIN information_schema.CHECK_CONSTRAINTS c ON c.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND c.CONSTRAINT_NAME=t.CONSTRAINT_NAME WHERE t.TABLE_SCHEMA=DATABASE() AND ((t.TABLE_NAME=''login_log'' AND c.CHECK_CLAUSE REGEXP ''(^|[^a-zA-Z0-9_])password([^a-zA-Z0-9_]|$)'') OR (t.TABLE_NAME=''mail_log'' AND c.CHECK_CLAUSE REGEXP ''(^|[^a-zA-Z0-9_])content([^a-zA-Z0-9_]|$)''))) INTO @aio_align_invalid';
    PREPARE aio_align_stmt FROM @aio_align_sql; SET prepared = TRUE;
    EXECUTE aio_align_stmt; DEALLOCATE PREPARE aio_align_stmt; SET prepared = FALSE;
    IF @aio_align_invalid THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Legacy log column has CHECK dependency; resolve explicitly before migration';
    END IF;
  END IF;

  OPEN targets;
  validate_data: LOOP
    FETCH targets INTO table_value, column_value, kind_value, type_value, nullable_value,
      default_value, extra_value, charset_value, collation_value, comment_value, target_comment;
    IF done THEN LEAVE validate_data; END IF;
    IF kind_value = 'INTEGER' THEN
      SET magnitude_expression = CONCAT('TRIM(LEADING ''0'' FROM IF(LEFT(`', column_value,
        '`,1)=''-'',SUBSTRING(`', column_value, '`,2),`', column_value, '`))');
      SET @aio_align_sql = CONCAT('SELECT EXISTS(SELECT 1 FROM `', table_value, '` WHERE `', column_value,
        '` IS NULL OR `', column_value, '` REGEXP ''[^0-9-]'' OR `', column_value,
        '` NOT REGEXP ''^-?[0-9]+$'' OR CHAR_LENGTH(', magnitude_expression, ')>19 OR (CHAR_LENGTH(',
        magnitude_expression, ')=19 AND BINARY ', magnitude_expression, '>IF(LEFT(`', column_value,
        '`,1)=''-'',''9223372036854775808'',''9223372036854775807'')) LIMIT 1) INTO @aio_align_invalid');
    ELSEIF kind_value IN ('OWNER', 'AMOUNT') THEN
      SET @aio_align_sql = CONCAT('SELECT EXISTS(SELECT 1 FROM `', table_value, '` WHERE `', column_value,
        '` IS NULL LIMIT 1) INTO @aio_align_invalid');
    ELSE
      ITERATE validate_data;
    END IF;
    PREPARE aio_align_stmt FROM @aio_align_sql; SET prepared = TRUE;
    EXECUTE aio_align_stmt; DEALLOCATE PREPARE aio_align_stmt; SET prepared = FALSE;
    IF @aio_align_invalid THEN
      SET problem = CONCAT('Invalid data in ', table_value, '.', column_value, '; no business ALTER applied');
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = problem;
    END IF;
  END LOOP;
  CLOSE targets;

  -- 只规范明确列属性，保留其他字段、现有值、字符集、排序规则和审计时间。
  SET done = FALSE;
  OPEN targets;
  apply_columns: LOOP
    FETCH targets INTO table_value, column_value, kind_value, type_value, nullable_value,
      default_value, extra_value, charset_value, collation_value, comment_value, target_comment;
    IF done OR (current_table <> '' AND current_table <> table_value) THEN
      IF changes <> '' THEN
        SET @aio_align_sql = CONCAT('ALTER TABLE `', current_table, '` ', changes);
        PREPARE aio_align_stmt FROM @aio_align_sql; SET prepared = TRUE;
        EXECUTE aio_align_stmt; DEALLOCATE PREPARE aio_align_stmt; SET prepared = FALSE;
      END IF;
      SET changes = '';
    END IF;
    IF done THEN LEAVE apply_columns; END IF;
    SET current_table = table_value;
    SET definition_value = '';
    IF kind_value = 'INTEGER' AND (type_value NOT IN ('bigint', 'bigint(20)') OR nullable_value <> 'NO') THEN
      SET definition_value = CONCAT('MODIFY COLUMN `', column_value, '` BIGINT NOT NULL COMMENT ', QUOTE(target_comment));
    ELSEIF kind_value IN ('OWNER', 'AMOUNT') AND nullable_value <> 'NO' THEN
      SET definition_value = CONCAT('MODIFY COLUMN `', column_value, '` ', type_value,
        ' NOT NULL COMMENT ', QUOTE(comment_value));
    ELSEIF kind_value = 'ASSIGNED_ID' AND extra_value = 'auto_increment' THEN
      SET definition_value = CONCAT('MODIFY COLUMN `', column_value, '` ', type_value,
        ' NOT NULL COMMENT ', QUOTE(comment_value));
    ELSEIF kind_value = 'NULL_DEFAULT' AND default_value IS NOT NULL THEN
      SET definition_value = CONCAT('MODIFY COLUMN `', column_value, '` ', type_value,
        ' CHARACTER SET ', charset_value, ' COLLATE ', collation_value,
        ' NULL DEFAULT NULL COMMENT ', QUOTE(comment_value));
    END IF;
    IF definition_value <> '' THEN
      SET changes = CONCAT(changes, IF(changes = '', '', ', '), definition_value);
    END IF;
  END LOOP;
  CLOSE targets;

  SET changes = '';
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_dict_data' AND INDEX_NAME = 'idx_user_dict_type') THEN
    SET changes = 'ADD INDEX `idx_user_dict_type` (`user_id`, `dict_type`)';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_dict_data' AND INDEX_NAME = 'idx_user_dict_template') THEN
    SET changes = CONCAT(changes, IF(changes = '', '', ', '), 'ADD INDEX `idx_user_dict_template` (`template_id`)');
  END IF;
  IF changes <> '' THEN
    SET @aio_align_sql = CONCAT('ALTER TABLE `user_dict_data` ', changes);
    PREPARE aio_align_stmt FROM @aio_align_sql; SET prepared = TRUE;
    EXECUTE aio_align_stmt; DEALLOCATE PREPARE aio_align_stmt; SET prepared = FALSE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_dict_type' AND INDEX_NAME = 'idx_user_dict_type_user') THEN
    ALTER TABLE `user_dict_type` ADD INDEX `idx_user_dict_type_user` (`user_id`, `dict_type`);
  END IF;

  -- 结构/数据预检及保留数据的 ALTER 成功后，最后删除废弃敏感列，不读取其内容。
  IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'login_log' AND COLUMN_NAME = 'password') THEN
    ALTER TABLE `login_log` DROP COLUMN `password`;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'mail_log' AND COLUMN_NAME = 'content') THEN
    ALTER TABLE `mail_log` DROP COLUMN `content`;
  END IF;
  DROP TEMPORARY TABLE `_aio_align_columns`;
  DROP TEMPORARY TABLE `_aio_align_indexes`;
  SET @aio_align_sql = NULL;
  SET @aio_align_invalid = NULL;
END$$
DELIMITER ;
SET SESSION sql_mode = @aio_align_saved_mode;
SET @aio_align_saved_mode = NULL;
CALL `_migrate_20261028_schema_alignment`();
DROP PROCEDURE `_migrate_20261028_schema_alignment`;
