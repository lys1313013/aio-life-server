-- 目的：设备和衣物保存平台、原始订单/商品ID、稳定明细标识及件序号，重新采集查询服务器。
-- 兼容：MySQL 5.7.44、8.0；不依赖CHECK或ADD COLUMN IF NOT EXISTS。
-- 前置：device、wardrobe_item为InnoDB/utf8mb4，包含用户、删除标记和插入位置所需列。
-- 顺序：20261028.06 历史接入边界之后的新版本；修复已登记基线但来源字段尚缺失的已有库。
-- 新库已由旧迁移补齐时仅校验并跳过；不修改、不重跑已登记的历史版本。
-- 执行：确认授权、停写并备份后，通过独立 Flyway compile flyway:migrate 执行；禁止跳过失败。
-- 权限：SELECT、ALTER、CREATE TEMPORARY TABLES、CREATE ROUTINE、ALTER ROUTINE、EXECUTE。
-- 影响：仅增加缺失列和查询索引；历史来源未知，新增列保持NULL，不回填或删除既有数据。
-- 比较：平台、订单ID、商品ID用ascii_bin；稳定明细标识用utf8mb4_bin，避免大小写折叠。
-- 唯一性：同用户事务先锁user行，再当前读两张表；按稳定明细标识和件序号识别，逻辑删除记录不命中。
-- 预检：所有表、已有来源列的类型/NULL/默认值/注释/排序规则及同名索引先统一检查，冲突发生在首个业务ALTER之前。
-- 重跑：支持无来源列、全量基线已有3列、任意部分列/索引已存在及已完成；匹配定义跳过，冲突不覆盖。
-- 恢复：DDL隐式提交；部分失败后保持停写，核对SHOW CREATE TABLE、修复冲突，再从头运行。
-- 回退：删除新增列会丢失新版写入的来源数据；应用回退及数据恢复须使用事前备份，不提供自动回退。

SET @aio_asset_saved_sql_mode=@@SESSION.sql_mode;
SET SESSION sql_mode='STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION';
DELIMITER $$
DROP PROCEDURE IF EXISTS `_migrate_20261029_asset_source_repair`$$
CREATE PROCEDURE `_migrate_20261029_asset_source_repair`()
BEGIN
  DECLARE v_done BOOLEAN DEFAULT FALSE;
  DECLARE v_prepared BOOLEAN DEFAULT FALSE;
  DECLARE v_table VARCHAR(64);
  DECLARE v_column VARCHAR(64);
  DECLARE v_type VARCHAR(64);
  DECLARE v_charset VARCHAR(64);
  DECLARE v_collation VARCHAR(64);
  DECLARE v_comment VARCHAR(255);
  DECLARE v_after VARCHAR(64);
  DECLARE v_current_table VARCHAR(64) DEFAULT '';
  DECLARE v_changes LONGTEXT DEFAULT '';
  DECLARE v_index VARCHAR(64);
  DECLARE v_count INT;
  DECLARE v_problem VARCHAR(128);
  DECLARE asset_columns CURSOR FOR
    SELECT table_name,column_name,column_type,charset_name,collation_name,column_comment,after_column
    FROM `_aio_asset_columns` ORDER BY table_name,position;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done=TRUE;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    IF v_prepared THEN DEALLOCATE PREPARE aio_asset_stmt; END IF;
    DROP TEMPORARY TABLE IF EXISTS `_aio_asset_columns`;
    SET @aio_asset_sql=NULL;
    SET @aio_asset_invalid=NULL;
    RESIGNAL;
  END;

  DROP TEMPORARY TABLE IF EXISTS `_aio_asset_columns`;
  CREATE TEMPORARY TABLE `_aio_asset_columns` (
    table_name VARCHAR(64) NOT NULL COMMENT '目标业务表',
    position INT NOT NULL COMMENT '同表新增字段顺序',
    column_name VARCHAR(64) NOT NULL COMMENT '目标来源字段',
    column_type VARCHAR(64) NOT NULL COMMENT '目标列类型，不含整数显示宽度',
    charset_name VARCHAR(64) NULL COMMENT '字符集，数值列为空',
    collation_name VARCHAR(64) NULL COMMENT '排序规则，数值列为空',
    column_comment VARCHAR(255) NOT NULL COMMENT '目标中文字段注释',
    after_column VARCHAR(64) NOT NULL COMMENT '新增列的前一字段',
    PRIMARY KEY (table_name,position)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='本次采集来源增量计划，连接级临时数据';
  -- 与当前实体和历史来源字段定义一致，所有来源列均为NULL DEFAULT NULL。
  INSERT INTO `_aio_asset_columns`
    (table_name,position,column_name,column_type,charset_name,collation_name,column_comment,after_column)
  VALUES
    ('device',1,'source_platform','varchar(16)','ascii','ascii_bin','采集平台：jd=京东、taobao=淘宝；手工录入或历史记录为NULL','file_id'),
    ('device',2,'source_order_id','varchar(100)','ascii','ascii_bin','来源订单ID，原始字符串；无采集来源时为NULL','source_platform'),
    ('device',3,'source_product_id','varchar(100)','ascii','ascii_bin','来源商品ID，采集行sku原始字符串；无采集来源时为NULL','source_order_id'),
    ('device',4,'source_item_id','varchar(128)','utf8mb4','utf8mb4_bin','订单内稳定商品明细标识，区分同商品的规格或多行；无采集来源时为NULL','source_product_id'),
    ('device',5,'source_unit_index','int',NULL,NULL,'来源商品行内件序号，从1开始；无采集来源时为NULL','source_item_id'),
    ('wardrobe_item',1,'source_platform','varchar(16)','ascii','ascii_bin','采集平台：jd=京东、taobao=淘宝；手工录入或历史记录为NULL','user_id'),
    ('wardrobe_item',2,'source_order_id','varchar(100)','ascii','ascii_bin','来源订单ID，原始字符串；无采集来源时为NULL','source_platform'),
    ('wardrobe_item',3,'source_product_id','varchar(100)','ascii','ascii_bin','来源商品ID，采集行sku原始字符串；无采集来源时为NULL','source_order_id'),
    ('wardrobe_item',4,'source_item_id','varchar(128)','utf8mb4','utf8mb4_bin','订单内稳定商品明细标识，区分同商品的规格或多行；无采集来源时为NULL','source_product_id'),
    ('wardrobe_item',5,'source_unit_index','int',NULL,NULL,'来源商品行内件序号，从1开始；无采集来源时为NULL','source_item_id');

  -- 表、插入位置及索引引用列必须已存在；不凭空创建业务表。
  SELECT MIN(p.table_name) INTO v_problem
  FROM (SELECT DISTINCT table_name FROM `_aio_asset_columns`) p
  LEFT JOIN information_schema.TABLES t ON t.TABLE_SCHEMA=DATABASE() AND t.TABLE_NAME=p.table_name
  WHERE t.TABLE_NAME IS NULL OR t.TABLE_TYPE<>'BASE TABLE' OR t.ENGINE<>'InnoDB'
    OR t.TABLE_COLLATION NOT LIKE 'utf8mb4%';
  IF v_problem IS NOT NULL THEN
    SET v_problem=CONCAT('Asset migration requires InnoDB/utf8mb4 base table: ',v_problem);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=v_problem;
  END IF;
  SELECT MIN(CONCAT(p.table_name,'.',p.required_column)) INTO v_problem
  FROM (SELECT 'device' AS table_name,'file_id' AS required_column
    UNION ALL SELECT 'device','user_id' UNION ALL SELECT 'device','is_deleted'
    UNION ALL SELECT 'wardrobe_item','user_id' UNION ALL SELECT 'wardrobe_item','is_deleted') p
  LEFT JOIN information_schema.COLUMNS c ON c.TABLE_SCHEMA=DATABASE()
    AND c.TABLE_NAME=p.table_name AND c.COLUMN_NAME=p.required_column
  WHERE c.COLUMN_NAME IS NULL;
  IF v_problem IS NOT NULL THEN
    SET v_problem=CONCAT('Asset migration missing prerequisite: ',v_problem);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=v_problem;
  END IF;

  -- 索引前导列必须可直接建立完整BTREE；先拒绝未知旧类型，避免第二表ALTER才失败。
  SELECT MIN(CONCAT(c.TABLE_NAME,'.',c.COLUMN_NAME)) INTO v_problem
  FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA=DATABASE()
    AND c.TABLE_NAME IN ('device','wardrobe_item') AND c.COLUMN_NAME IN ('user_id','is_deleted')
    AND ((c.COLUMN_NAME='user_id' AND (c.DATA_TYPE<>'bigint' OR c.COLUMN_TYPE LIKE '%unsigned%'
      OR c.COLUMN_TYPE LIKE '%zerofill%'))
      OR (c.COLUMN_NAME='is_deleted' AND c.DATA_TYPE NOT IN ('tinyint','smallint','mediumint','int','bigint'))
      OR COALESCE(c.GENERATION_EXPRESSION,'')<>'');
  IF v_problem IS NOT NULL THEN
    SET v_problem=CONCAT('Asset index prerequisite type conflict: ',v_problem);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=v_problem;
  END IF;

  SELECT MIN(CONCAT(p.table_name,'.',p.column_name)) INTO v_problem
  FROM `_aio_asset_columns` p JOIN information_schema.COLUMNS c ON c.TABLE_SCHEMA=DATABASE()
    AND c.TABLE_NAME=p.table_name AND c.COLUMN_NAME=p.column_name
  WHERE NOT (LOWER(c.COLUMN_TYPE)=p.column_type OR (p.column_type='int' AND LOWER(c.COLUMN_TYPE) REGEXP '^int([(][0-9]+[)])?$'))
    OR c.IS_NULLABLE<>'YES' OR c.COLUMN_DEFAULT IS NOT NULL
    OR COALESCE(c.EXTRA,'')<>'' OR COALESCE(c.GENERATION_EXPRESSION,'')<>''
    OR NOT (c.CHARACTER_SET_NAME <=> p.charset_name) OR NOT (c.COLLATION_NAME <=> p.collation_name)
    OR BINARY c.COLUMN_COMMENT<>BINARY p.column_comment;
  IF v_problem IS NOT NULL THEN
    SET v_problem=CONCAT('Asset column definition conflict: ',v_problem);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=v_problem;
  END IF;

  -- 同名索引必须完整覆盖用户、平台、订单和删除状态；不替换自定义索引。
  SELECT MIN(CONCAT(x.table_name,'.',x.index_name)) INTO v_problem FROM (
    SELECT TABLE_NAME AS table_name,INDEX_NAME AS index_name
    FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
      AND ((TABLE_NAME='device' AND INDEX_NAME='idx_device_capture_order')
        OR (TABLE_NAME='wardrobe_item' AND INDEX_NAME='idx_wardrobe_item_capture_order'))
    GROUP BY TABLE_NAME,INDEX_NAME
    HAVING COUNT(*)<>4 OR SUM(NON_UNIQUE<>1 OR INDEX_TYPE<>'BTREE' OR SUB_PART IS NOT NULL
      OR NOT (COLLATION <=> 'A')
      OR NOT (COLUMN_NAME <=> CASE SEQ_IN_INDEX
        WHEN 1 THEN 'user_id' WHEN 2 THEN 'source_platform' WHEN 3 THEN 'source_order_id' WHEN 4 THEN 'is_deleted' END))>0
  ) x;
  IF v_problem IS NOT NULL THEN
    SET v_problem=CONCAT('Asset index definition conflict: ',v_problem);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=v_problem;
  END IF;
  IF EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA='information_schema' AND TABLE_NAME='STATISTICS' AND COLUMN_NAME='IS_VISIBLE') THEN
    SET @aio_asset_sql='SELECT COUNT(*) INTO @aio_asset_invalid FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND IS_VISIBLE<>''YES'' AND ((TABLE_NAME=''device'' AND INDEX_NAME=''idx_device_capture_order'') OR (TABLE_NAME=''wardrobe_item'' AND INDEX_NAME=''idx_wardrobe_item_capture_order''))';
    PREPARE aio_asset_stmt FROM @aio_asset_sql;
    SET v_prepared=TRUE;
    EXECUTE aio_asset_stmt;
    DEALLOCATE PREPARE aio_asset_stmt;
    SET v_prepared=FALSE;
    IF @aio_asset_invalid>0 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Asset index must be visible';
    END IF;
  END IF;

  SET v_done=FALSE;
  OPEN asset_columns;
  apply_columns: LOOP
    FETCH asset_columns INTO v_table,v_column,v_type,v_charset,v_collation,v_comment,v_after;
    IF v_done OR (v_current_table<>'' AND v_current_table<>v_table) THEN
      SET v_index=IF(v_current_table='device','idx_device_capture_order','idx_wardrobe_item_capture_order');
      SELECT COUNT(*) INTO v_count FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=v_current_table AND INDEX_NAME=v_index;
      IF v_count=0 THEN
        SET v_changes=CONCAT(v_changes,IF(v_changes='','',', '),'ADD INDEX `',v_index,
          '` (`user_id`,`source_platform`,`source_order_id`,`is_deleted`)');
      END IF;
      IF v_changes<>'' THEN
        SET @aio_asset_sql=CONCAT('ALTER TABLE `',v_current_table,'` ',v_changes);
        PREPARE aio_asset_stmt FROM @aio_asset_sql;
        SET v_prepared=TRUE;
        EXECUTE aio_asset_stmt;
        DEALLOCATE PREPARE aio_asset_stmt;
        SET v_prepared=FALSE;
      END IF;
      SET v_changes='';
    END IF;
    IF v_done THEN LEAVE apply_columns; END IF;
    SET v_current_table=v_table;
    SELECT COUNT(*) INTO v_count FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=v_table AND COLUMN_NAME=v_column;
    IF v_count=0 THEN
      SET v_changes=CONCAT(v_changes,IF(v_changes='','',', '),'ADD COLUMN `',v_column,'` ',v_type,
        IF(v_charset IS NULL,'',CONCAT(' CHARACTER SET ',v_charset,' COLLATE ',v_collation)),
        ' NULL DEFAULT NULL COMMENT ',QUOTE(v_comment),' AFTER `',v_after,'`');
    END IF;
  END LOOP;
  CLOSE asset_columns;
  DROP TEMPORARY TABLE `_aio_asset_columns`;
  SET @aio_asset_sql=NULL;
  SET @aio_asset_invalid=NULL;
END$$
DELIMITER ;
SET SESSION sql_mode=@aio_asset_saved_sql_mode;
SET @aio_asset_saved_sql_mode=NULL;
CALL `_migrate_20261029_asset_source_repair`();
DROP PROCEDURE `_migrate_20261029_asset_source_repair`;
