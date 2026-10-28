-- 目的：同步已核实的73处字段注释与7处表注释，含30个原空字段注释；不改变业务数据。
-- 兼容：MySQL 5.7.44、8.0；字段定义由目标库元数据重建，不依赖CHECK或8.0专属字段。
-- 前置：完成适用的01/02/03增量；全部目标表为InnoDB，目标列存在且基础类型符合清单。
-- 语义：goal.type按当前GoalTypeEnum的十类目标；会员分类包括lifestyle/reading。
--       密码库只描述当前可确认的客户端加密边界，不承诺未实现的GCM认证模式或统一账号密文格式。
-- 影响：仅COMMENT；保留每个字段的类型、长度、精度、NULL、默认值、字符集、排序规则、自增、ON UPDATE、位置及索引。
--       不增删表列，不回填或转换任何数据；文章两表的用途、归属及约束仍待单独确认。
-- 执行：停写并核对目标库，保留结构备份；utf8mb4连接，用支持DELIMITER的mysql客户端，遇错停止，禁止--force。
-- 权限：information_schema读取、目标表ALTER、CREATE TEMPORARY TABLES、CREATE/ALTER ROUTINE和EXECUTE。
-- 预检：在第一个业务ALTER前检查全部目标表、列、基础类型及可安全保留的默认/EXTRA；不支持的定义报错。
-- 重跑：注释一致不执行ALTER；支持部分表完成后从头重跑。DDL隐式提交，不能用ROLLBACK恢复旧注释。
-- 顺序：新库在全量基线和01/02/03之后、种子之前执行；不修改全量初始化基线。

DROP PROCEDURE IF EXISTS `_migrate_20261028_schema_comments`;
SET @aio_sc_saved_sql_mode=@@SESSION.sql_mode;
SET SESSION sql_mode='STRICT_ALL_TABLES,NO_ENGINE_SUBSTITUTION';
DELIMITER $$
CREATE PROCEDURE `_migrate_20261028_schema_comments`()
BEGIN
  DECLARE finished BOOLEAN DEFAULT FALSE;
  DECLARE prepared BOOLEAN DEFAULT FALSE;
  DECLARE table_value VARCHAR(64);
  DECLARE column_value VARCHAR(64);
  DECLARE data_type_value VARCHAR(64);
  DECLARE column_type_value TEXT;
  DECLARE nullable_value VARCHAR(3);
  DECLARE default_value TEXT;
  DECLARE extra_value TEXT;
  DECLARE charset_value VARCHAR(64);
  DECLARE collation_value VARCHAR(64);
  DECLARE comment_value TEXT;
  DECLARE target_comment TEXT;
  DECLARE current_table VARCHAR(64) DEFAULT '';
  DECLARE changes LONGTEXT DEFAULT '';
  DECLARE definition_value LONGTEXT;
  DECLARE problem VARCHAR(128);
  DECLARE target_columns CURSOR FOR
    SELECT s.table_name,s.column_name,c.DATA_TYPE,c.COLUMN_TYPE,c.IS_NULLABLE,
      c.COLUMN_DEFAULT,c.EXTRA,c.CHARACTER_SET_NAME,c.COLLATION_NAME,c.COLUMN_COMMENT,s.target_comment
    FROM `_aio_sc_columns` s JOIN information_schema.COLUMNS c
      ON c.TABLE_SCHEMA=DATABASE() AND c.TABLE_NAME=s.table_name AND c.COLUMN_NAME=s.column_name
    ORDER BY s.table_name,c.ORDINAL_POSITION;
  DECLARE target_tables CURSOR FOR
    SELECT s.table_name,t.TABLE_COMMENT,s.target_comment FROM `_aio_sc_tables` s
      JOIN information_schema.TABLES t ON t.TABLE_SCHEMA=DATABASE() AND t.TABLE_NAME=s.table_name
    ORDER BY s.table_name;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished=TRUE;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    IF prepared THEN DEALLOCATE PREPARE aio_sc_stmt; END IF;
    DROP TEMPORARY TABLE IF EXISTS `_aio_sc_columns`;
    DROP TEMPORARY TABLE IF EXISTS `_aio_sc_tables`;
    SET @aio_sc_sql=NULL;
    RESIGNAL;
  END;
  IF DATABASE() IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Select the target database before running this migration';
  END IF;
  DROP TEMPORARY TABLE IF EXISTS `_aio_sc_columns`;
  DROP TEMPORARY TABLE IF EXISTS `_aio_sc_tables`;
  CREATE TEMPORARY TABLE `_aio_sc_columns` (
    `table_name` VARCHAR(64) NOT NULL COMMENT '待修改注释的业务表名',
    `column_name` VARCHAR(64) NOT NULL COMMENT '待修改注释的业务字段名',
    `expected_data_type` VARCHAR(64) NOT NULL COMMENT '预期基础类型，用于阻止误操作未知结构',
    `target_comment` VARCHAR(1024) NOT NULL COMMENT '经代码语义核实的中文字段注释',
    -- 每个字段最多一条注释变更。
    PRIMARY KEY (`table_name`,`column_name`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='本次注释迁移的临时列计划';
  CREATE TEMPORARY TABLE `_aio_sc_tables` (
    `table_name` VARCHAR(64) NOT NULL COMMENT '待修改注释的业务表名',
    `target_comment` VARCHAR(1024) NOT NULL COMMENT '经业务语义核实的中文表注释',
    -- 每张表最多一条表注释变更。
    PRIMARY KEY (`table_name`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='本次注释迁移的临时表计划';
  INSERT INTO `_aio_sc_columns` (`table_name`,`column_name`,`expected_data_type`,`target_comment`) VALUES
    ('b_video', 'last_watched', 'datetime', '最后观看时间（syncProgress 同步时写入）'),
    ('device', 'spec', 'varchar', '设备规格'),
    ('file', 'file_name', 'varchar', 'MinIO完整对象键'),
    ('goal', 'type', 'tinyint', '目标类型：1=日目标，2=周目标，3=月度目标，4=季度目标，5=半年目标，6=年度目标，7=三年目标，8=五年目标，9=十年目标，10=人生目标'),
    ('life_footprint', 'region_code', 'varchar', '所选最深层地区编码，可停在省市区县或乡镇，未选时为空'),
    ('login_log', 'ip_address', 'varchar', 'IP地址'),
    ('membership_provider', 'category', 'varchar', '默认分类:video/music/shopping/lifestyle/cloud/reading/study/game/AI/other'),
    ('membership_record', 'category', 'varchar', '分类:video/music/shopping/lifestyle/cloud/reading/study/game/AI/other'),
    ('membership_record', 'provider_id', 'bigint', '会员平台ID，未关联平台时可为空'),
    ('password_vault', 'password', 'text', '客户端加密的密码密文；服务端不进行加解密'),
    ('password_vault', 'remark', 'text', '客户端加密的备注密文；服务端不进行加解密'),
    ('password_vault', 'salt', 'varchar', 'PBKDF2盐值，每条记录唯一（前端生成）'),
    ('password_vault', 'username', 'text', '账号内容，由客户端提交，服务端不进行加解密'),
    ('read_record', 'finish_time', 'datetime', '完成时间'),
    ('record_article', 'content_html', 'longtext', '文章HTML内容'),
    ('record_article', 'create_time', 'datetime', '创建时间'),
    ('record_article', 'create_user', 'bigint', '创建人'),
    ('record_article', 'id', 'bigint', '文章ID'),
    ('record_article', 'is_deleted', 'tinyint', '是否删除'),
    ('record_article', 'update_time', 'datetime', '更新时间'),
    ('record_article', 'update_user', 'bigint', '更新人'),
    ('record_article', 'url', 'varchar', '原文链接'),
    ('record_article_annotation', 'color', 'varchar', '标注颜色'),
    ('record_article_annotation', 'create_time', 'datetime', '创建时间'),
    ('record_article_annotation', 'create_user', 'bigint', '创建人'),
    ('record_article_annotation', 'end_container_path', 'varchar', '结束容器路径'),
    ('record_article_annotation', 'end_offset', 'int', '结束偏移量'),
    ('record_article_annotation', 'id', 'bigint', '标注ID'),
    ('record_article_annotation', 'is_deleted', 'tinyint', '是否删除'),
    ('record_article_annotation', 'note_content', 'text', '标注内容'),
    ('record_article_annotation', 'selected_text', 'text', '选中文本'),
    ('record_article_annotation', 'start_container_path', 'varchar', '起始容器路径'),
    ('record_article_annotation', 'start_offset', 'int', '起始偏移量'),
    ('record_article_annotation', 'update_time', 'datetime', '更新时间'),
    ('record_article_annotation', 'update_user', 'bigint', '更新人'),
    ('sys_activity_log', 'access_type', 'varchar', '访问类型'),
    ('sys_activity_log', 'browser', 'varchar', '浏览器'),
    ('sys_activity_log', 'create_time', 'datetime', '创建时间'),
    ('sys_activity_log', 'create_user', 'bigint', '创建人'),
    ('sys_activity_log', 'duration_ms', 'int', '处理耗时毫秒，非负，0有效，NULL未采集'),
    ('sys_activity_log', 'function_item', 'varchar', '功能项'),
    ('sys_activity_log', 'function_name', 'varchar', '功能名称'),
    ('sys_activity_log', 'id', 'bigint', '主键ID'),
    ('sys_activity_log', 'ip_address', 'varchar', 'IP 地址'),
    ('sys_activity_log', 'is_deleted', 'tinyint', '是否删除(0-否,1-是)'),
    ('sys_activity_log', 'nickname', 'varchar', '昵称'),
    ('sys_activity_log', 'request_method', 'varchar', 'HTTP方法，未采集时为NULL'),
    ('sys_activity_log', 'request_params', 'json', '白名单脱敏参数，应用限制32KiB'),
    ('sys_activity_log', 'request_time', 'datetime', '服务端请求接收时间，Asia/Shanghai'),
    ('sys_activity_log', 'server_ip', 'varchar', '处理请求的后端实例IPv4或IPv6'),
    ('sys_activity_log', 'success', 'tinyint', '是否成功'),
    ('sys_activity_log', 'update_time', 'datetime', '更新时间'),
    ('sys_activity_log', 'update_user', 'bigint', '更新人'),
    ('sys_activity_log', 'user_id', 'bigint', '操作用户ID'),
    ('sys_activity_log', 'username', 'varchar', '用户名'),
    ('task_detail', 'priority', 'int', '优先级: 1-高, 10-中, 20-低'),
    ('user_bind', 'platform', 'varchar', '平台类型：github, leetcode, csdn, shanbay, douban, weread'),
    ('user_dict_data', 'color', 'varchar', '颜色值(Hex)'),
    ('user_dict_data', 'create_user', 'bigint', '创建人ID'),
    ('user_dict_data', 'dict_label', 'varchar', '字典标签(分类名称)'),
    ('user_dict_data', 'dict_value', 'varchar', '字典键值(分类标识)'),
    ('user_dict_data', 'icon', 'varchar', '图标名称(Iconify格式)'),
    ('user_dict_data', 'id', 'bigint', '字典主键'),
    ('user_dict_data', 'is_deleted', 'tinyint', '是否删除 0-未删除 1-已删除'),
    ('user_dict_data', 'update_user', 'bigint', '更新人ID'),
    ('user_dict_data', 'user_id', 'bigint', '所属用户ID'),
    ('user_dict_type', 'color', 'varchar', '颜色'),
    ('user_dict_type', 'create_user', 'bigint', '创建人'),
    ('user_dict_type', 'icon', 'varchar', '图标'),
    ('user_dict_type', 'is_deleted', 'tinyint', '是否删除'),
    ('user_dict_type', 'sys_dict_id', 'bigint', '关联的系统字典类型ID'),
    ('user_dict_type', 'update_user', 'bigint', '更新人'),
    ('user_dict_type', 'user_id', 'bigint', '所属用户ID');
  INSERT INTO `_aio_sc_tables` (`table_name`,`target_comment`) VALUES
    ('file', '系统文件表'),
    ('message', '消息表'),
    ('password_vault', '密码库表（密码及备注由客户端加密，服务端不进行加解密）'),
    ('record_article', '收藏文章表'),
    ('record_article_annotation', '文章标注表'),
    ('thought_rela_event', '思考关联事件表'),
    ('user_dict_data', '用户字典数据表');

  -- 所有元数据检查必须先于任何业务表ALTER，包括只修改表注释的目标。
  SELECT MIN(CONCAT(s.table_name,'.',s.column_name)) INTO problem
    FROM `_aio_sc_columns` s
    LEFT JOIN information_schema.TABLES t ON t.TABLE_SCHEMA=DATABASE() AND t.TABLE_NAME=s.table_name
    LEFT JOIN information_schema.COLUMNS c ON c.TABLE_SCHEMA=DATABASE()
      AND c.TABLE_NAME=s.table_name AND c.COLUMN_NAME=s.column_name
    WHERE t.TABLE_NAME IS NULL OR t.TABLE_TYPE<>'BASE TABLE' OR t.ENGINE<>'InnoDB' OR c.COLUMN_NAME IS NULL;
  IF problem IS NOT NULL THEN
    SET problem=CONCAT('Missing column or non-InnoDB base table: ',problem);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=problem;
  END IF;
  SELECT MIN(s.table_name) INTO problem FROM `_aio_sc_tables` s
    LEFT JOIN information_schema.TABLES t ON t.TABLE_SCHEMA=DATABASE() AND t.TABLE_NAME=s.table_name
    WHERE t.TABLE_NAME IS NULL OR t.TABLE_TYPE<>'BASE TABLE' OR t.ENGINE<>'InnoDB';
  IF problem IS NOT NULL THEN
    SET problem=CONCAT('Missing or non-InnoDB base table: ',problem);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=problem;
  END IF;
  SELECT MIN(CONCAT(s.table_name,'.',s.column_name)) INTO problem
    FROM `_aio_sc_columns` s JOIN information_schema.COLUMNS c
      ON c.TABLE_SCHEMA=DATABASE() AND c.TABLE_NAME=s.table_name AND c.COLUMN_NAME=s.column_name
    WHERE c.DATA_TYPE<>s.expected_data_type OR COALESCE(c.GENERATION_EXPRESSION,'')<>''
      OR NOT (TRIM(REPLACE(LOWER(c.EXTRA),'default_generated','')) IN ('','auto_increment')
        OR TRIM(REPLACE(LOWER(c.EXTRA),'default_generated','')) REGEXP '^on update current_timestamp([(][0-6]?[)])?$')
      OR (LOWER(c.EXTRA) LIKE '%default_generated%' AND NOT (c.DATA_TYPE IN ('datetime','timestamp')
        AND LOWER(COALESCE(c.COLUMN_DEFAULT,'')) REGEXP '^current_timestamp([(][0-6]?[)])?$'));
  IF problem IS NOT NULL THEN
    SET problem=CONCAT('Unsupported column definition: ',problem,'; inspect SHOW CREATE TABLE');
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=problem;
  END IF;

  -- 按表合并MODIFY，只替换COMMENT，其余属性来自目标库当前元数据。
  OPEN target_columns;
  apply_columns: LOOP
    FETCH target_columns INTO table_value,column_value,data_type_value,column_type_value,nullable_value,
      default_value,extra_value,charset_value,collation_value,comment_value,target_comment;
    IF finished OR (current_table<>'' AND current_table<>table_value) THEN
      IF changes<>'' THEN
        SET @aio_sc_sql=CONCAT('ALTER TABLE `',current_table,'` ',changes);
        PREPARE aio_sc_stmt FROM @aio_sc_sql;
        SET prepared=TRUE;
        EXECUTE aio_sc_stmt;
        DEALLOCATE PREPARE aio_sc_stmt;
        SET prepared=FALSE;
      END IF;
      SET changes='';
    END IF;
    IF finished THEN LEAVE apply_columns; END IF;
    SET current_table=table_value;
    IF BINARY comment_value<>BINARY target_comment THEN
      SET extra_value=TRIM(REPLACE(LOWER(extra_value),'default_generated',''));
      SET definition_value=CONCAT('MODIFY COLUMN `',column_value,'` ',column_type_value,
        IF(charset_value IS NULL,'',CONCAT(' CHARACTER SET ',charset_value,' COLLATE ',collation_value)),
        IF(nullable_value='YES',' NULL',' NOT NULL'));
      IF default_value IS NOT NULL THEN
        SET definition_value=CONCAT(definition_value,' DEFAULT ',
          IF(data_type_value IN ('datetime','timestamp') AND LOWER(default_value)
            REGEXP '^current_timestamp([(][0-6]?[)])?$',default_value,QUOTE(default_value)));
      ELSEIF nullable_value='YES' THEN
        SET definition_value=CONCAT(definition_value,' DEFAULT NULL');
      END IF;
      SET definition_value=CONCAT(definition_value,
        IF(extra_value='','',CONCAT(' ',extra_value)),' COMMENT ',QUOTE(target_comment));
      SET changes=CONCAT(changes,IF(changes='','',', '),definition_value);
    END IF;
  END LOOP;
  CLOSE target_columns;
  SET finished=FALSE;
  OPEN target_tables;
  apply_tables: LOOP
    FETCH target_tables INTO table_value,comment_value,target_comment;
    IF finished THEN LEAVE apply_tables; END IF;
    IF BINARY comment_value<>BINARY target_comment THEN
      SET @aio_sc_sql=CONCAT('ALTER TABLE `',table_value,'` COMMENT=',QUOTE(target_comment));
      PREPARE aio_sc_stmt FROM @aio_sc_sql;
      SET prepared=TRUE;
      EXECUTE aio_sc_stmt;
      DEALLOCATE PREPARE aio_sc_stmt;
      SET prepared=FALSE;
    END IF;
  END LOOP;
  CLOSE target_tables;
  DROP TEMPORARY TABLE `_aio_sc_columns`;
  DROP TEMPORARY TABLE `_aio_sc_tables`;
  SET @aio_sc_sql=NULL;
END$$
DELIMITER ;
SET SESSION sql_mode=@aio_sc_saved_sql_mode;
SET @aio_sc_saved_sql_mode=NULL;
CALL `_migrate_20261028_schema_comments`();
DROP PROCEDURE `_migrate_20261028_schema_comments`;
