-- 目的：统一排序、普通备注及启用字段；23列，涉及18张表。
-- 兼容：MySQL 5.7.44 / 8.0；使用 CHANGE COLUMN，不依赖 CHECK 约束。
-- 前置：当前全量基线或已有等价业务表；停写并备份，应用与三端同步发布。
-- 执行：mysql 指定目标库后执行本文件，遇错停止，禁止 --force；不指定 USE。
-- 影响：保留排序值、备注、默认值、NULL属性和索引；四张字典表旧0启用转换为新1启用。
-- 索引：CHANGE COLUMN 自动更新索引引用，不改变索引名称、顺序或唯一性。
-- 重跑：已改名跳过；字典暂存列和迁移注释标识支持中断续跑，不会重复反转。
-- 回滚：不能整体事务回滚；中断后保持停写、修复错误并重跑；应用回滚需恢复备份。
-- 暂存：__aio_legacy_status 仅供迁移恢复，完成后删除，不供应用兼容读取。

SET @aio_previous_sql_mode=@@SESSION.sql_mode;
SET SESSION sql_mode='STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION';

DELIMITER $$
DROP PROCEDURE IF EXISTS aio_unify_field$$
CREATE PROCEDURE aio_unify_field(
  IN p_table VARCHAR(64), IN p_old VARCHAR(64), IN p_new VARCHAR(64),
  IN p_comment VARCHAR(255), IN p_reverse TINYINT, IN p_apply TINYINT
)
proc: BEGIN
  DECLARE v_old INT DEFAULT 0;
  DECLARE v_new INT DEFAULT 0;
  DECLARE v_stage INT DEFAULT 0;
  DECLARE v_indexes INT DEFAULT 0;
  DECLARE v_source VARCHAR(64);
  DECLARE v_type VARCHAR(255);
  DECLARE v_nullable VARCHAR(3);
  DECLARE v_default TEXT;
  DECLARE v_charset VARCHAR(64);
  DECLARE v_collation VARCHAR(64);
  DECLARE v_extra VARCHAR(255);
  DECLARE v_definition TEXT;
  DECLARE v_marker VARCHAR(255);
  DECLARE v_error VARCHAR(128);
  SET v_error = CONCAT('字段迁移条件不满足: ', p_table, '.', p_old);
  SELECT COUNT(*) INTO v_old FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name=p_table AND column_name=p_old;
  SELECT COUNT(*), MAX(column_comment) INTO v_new, v_marker FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name=p_table AND column_name=p_new;
  SELECT COUNT(*) INTO v_stage FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name=p_table AND column_name='__aio_legacy_status';
  IF (v_old=1 AND v_new=1) OR (v_old=0 AND v_new=0)
      OR (p_reverse=1 AND v_stage=1 AND (v_old=1 OR v_marker<>'字段统一迁移中，禁止业务写入')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=v_error;
  END IF;
  -- 启用值必须是0/1；已有异常不会被转换成停用或默认值。
  IF (p_reverse=0 OR (v_old=0 AND v_stage=0)) AND p_new LIKE '%is_enabled' THEN
    SET @aio_sql=CONCAT('SELECT COUNT(*) INTO @aio_invalid FROM `',p_table,'` WHERE `',IF(v_old=1,p_old,p_new),
      '` IS NOT NULL AND `',IF(v_old=1,p_old,p_new),'` NOT IN (0,1)');
    PREPARE aio_stmt FROM @aio_sql; EXECUTE aio_stmt; DEALLOCATE PREPARE aio_stmt;
    IF @aio_invalid>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=v_error; END IF;
  END IF;
  IF v_old=0 AND (p_reverse=0 OR v_stage=0) THEN
    IF p_reverse=1 THEN
      SELECT column_type INTO v_type FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name=p_table AND column_name=p_new;
      IF v_type NOT LIKE 'tinyint%' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=v_error; END IF;
    END IF;
    LEAVE proc;
  END IF;
  SET v_source=IF(p_reverse=1 AND v_stage=1,'__aio_legacy_status',p_old);
  SELECT column_type,is_nullable,column_default,character_set_name,collation_name,extra
    INTO v_type,v_nullable,v_default,v_charset,v_collation,v_extra
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name=p_table AND column_name=v_source;
  IF p_reverse=1 THEN
    -- 当前四张字典表的状态列均无索引。自定义索引需要单独设计，禁止悄悄删除。
    SELECT COUNT(*) INTO v_indexes FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name=p_table AND column_name=v_source;
    IF v_indexes>0 OR (v_default IS NOT NULL AND v_default NOT IN ('0','1')) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=v_error;
    END IF;
    IF v_type<>'char(1)' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=v_error; END IF;
    SET @aio_sql=CONCAT('SELECT COUNT(*) INTO @aio_invalid FROM `',p_table,'` WHERE `',v_source,
      '` IS NOT NULL AND BINARY `',v_source,'` NOT IN (''0'',''1'')');
    PREPARE aio_stmt FROM @aio_sql; EXECUTE aio_stmt; DEALLOCATE PREPARE aio_stmt;
    IF @aio_invalid>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=v_error; END IF;
  END IF;
  IF p_apply=0 THEN LEAVE proc; END IF;
  IF p_reverse=0 THEN
    SET v_definition=CONCAT(v_type,
      IF(v_charset IS NULL,'',CONCAT(' CHARACTER SET ',v_charset,' COLLATE ',v_collation)),
      IF(v_nullable='YES',' NULL',' NOT NULL'),
      IF(v_default IS NULL,IF(v_nullable='YES',' DEFAULT NULL',''),CONCAT(' DEFAULT ',QUOTE(v_default))),
      IF(v_extra='','',CONCAT(' ',v_extra)), ' COMMENT ',QUOTE(p_comment));
    SET @aio_sql=CONCAT('ALTER TABLE `',p_table,'` CHANGE COLUMN `',p_old,'` `',p_new,'` ',v_definition);
    PREPARE aio_stmt FROM @aio_sql; EXECUTE aio_stmt; DEALLOCATE PREPARE aio_stmt;
  ELSE
    -- 在同一 ALTER 内建立恢复标记；旧值保留到回填成功，避免错误重跑造成二次反转。
    IF v_stage=0 THEN
      SET @aio_sql=CONCAT('ALTER TABLE `',p_table,'` CHANGE COLUMN `',p_old,
        '` `__aio_legacy_status` CHAR(1)',IF(v_nullable='YES',' NULL',' NOT NULL'),
        IF(v_default IS NULL,IF(v_nullable='YES',' DEFAULT NULL',''),CONCAT(' DEFAULT ',QUOTE(v_default))),
        ' COMMENT ''旧启用值，仅供本次迁移恢复'', ADD COLUMN `',p_new,
        '` TINYINT NULL DEFAULT NULL COMMENT ''字段统一迁移中，禁止业务写入''');
      PREPARE aio_stmt FROM @aio_sql; EXECUTE aio_stmt; DEALLOCATE PREPARE aio_stmt;
    END IF;
    -- 显式保留 update_time，防止 ON UPDATE 改写业务审计时间。
    SET @aio_sql=CONCAT('UPDATE `',p_table,'` SET `',p_new,
      '`=CASE `__aio_legacy_status` WHEN ''0'' THEN 1 WHEN ''1'' THEN 0 ELSE NULL END, update_time=update_time');
    PREPARE aio_stmt FROM @aio_sql; EXECUTE aio_stmt; DEALLOCATE PREPARE aio_stmt;
    -- 同步转换原默认值：旧0变1、旧1变0；NULL默认值及NULL行保留。
    SET @aio_sql=CONCAT('ALTER TABLE `',p_table,'` DROP COLUMN `__aio_legacy_status`, MODIFY COLUMN `',p_new,
      '` TINYINT',IF(v_nullable='YES',' NULL',' NOT NULL'),
      IF(v_default IS NULL,IF(v_nullable='YES',' DEFAULT NULL',''),CONCAT(' DEFAULT ',IF(v_default='0','1','0'))),
      ' COMMENT ',QUOTE(p_comment));
    PREPARE aio_stmt FROM @aio_sql; EXECUTE aio_stmt; DEALLOCATE PREPARE aio_stmt;
  END IF;
END$$
DELIMITER ;

-- 第一轮统一预检，异常值或新旧列冲突在修改任何业务表前报告。
CALL aio_unify_field('sys_menu', 'sort', 'sort_order', '排序值，数值越小越靠前', 0, 0);
CALL aio_unify_field('sys_dict_data', 'dict_sort', 'sort_order', '排序值，数值越小越靠前', 0, 0);
CALL aio_unify_field('user_dict_data', 'dict_sort', 'sort_order', '排序值，数值越小越靠前', 0, 0);
CALL aio_unify_field('time_tracker_category', 'sort', 'sort_order', '排序值，数值越小越靠前', 0, 0);
CALL aio_unify_field('task_detail', 'sort', 'sort_order', '排序值，数值越小越靠前', 0, 0);
CALL aio_unify_field('wardrobe_category', 'sort', 'sort_order', '排序值，数值越小越靠前', 0, 0);
CALL aio_unify_field('goal', 'pinned_sort', 'pinned_sort_order', '首页固定排序，数值越小越靠前', 0, 0);
CALL aio_unify_field('anniversary_record', 'pinned_sort', 'pinned_sort_order', '首页固定排序，数值越小越靠前', 0, 0);
CALL aio_unify_field('anniversary_record', 'note', 'remark', '备注，未填写时为空', 0, 0);
CALL aio_unify_field('membership_record', 'note', 'remark', '备注，未填写时为空', 0, 0);
CALL aio_unify_field('wardrobe_item', 'memo', 'remark', '备注，未填写时为空', 0, 0);
CALL aio_unify_field('life_footprint', 'note', 'remark', '备注，未填写时为空，应用限制5000字符', 0, 0);
CALL aio_unify_field('user_quick_nav', 'enabled', 'is_enabled', '是否启用：1=启用，0=停用', 0, 0);
CALL aio_unify_field('user_home_card_preference', 'enabled', 'is_enabled', '是否启用：1=启用，0=停用', 0, 0);
CALL aio_unify_field('notification_channel_config', 'enabled', 'is_enabled', '是否启用：1=启用，0=停用', 0, 0);
CALL aio_unify_field('notification_preference', 'enabled', 'is_enabled', '是否启用：1=启用，0=停用', 0, 0);
CALL aio_unify_field('sys_scheduled_job', 'enabled', 'is_enabled', '是否启用：1=启用，0=停用；不限制手动执行', 0, 0);
CALL aio_unify_field('sys_dict_type', 'status', 'is_enabled', '是否启用：1=启用，0=停用；NULL表示未设置', 1, 0);
CALL aio_unify_field('sys_dict_data', 'status', 'is_enabled', '是否启用：1=启用，0=停用；NULL表示未设置', 1, 0);
CALL aio_unify_field('user_dict_type', 'status', 'is_enabled', '是否启用：1=启用，0=停用；NULL表示未设置', 1, 0);
CALL aio_unify_field('user_dict_data', 'status', 'is_enabled', '是否启用：1=启用，0=停用；NULL表示未设置，个人覆盖项继承基础值', 1, 0);
CALL aio_unify_field('sys_menu', 'status', 'web_is_enabled', 'Web端是否启用：1=启用，0=停用', 0, 0);
CALL aio_unify_field('sys_menu', 'mobile_status', 'mobile_is_enabled', '移动端是否启用：1=启用，0=停用', 0, 0);

-- 第二轮执行；中断后重复运行整个文件。
CALL aio_unify_field('sys_menu', 'sort', 'sort_order', '排序值，数值越小越靠前', 0, 1);
CALL aio_unify_field('sys_dict_data', 'dict_sort', 'sort_order', '排序值，数值越小越靠前', 0, 1);
CALL aio_unify_field('user_dict_data', 'dict_sort', 'sort_order', '排序值，数值越小越靠前', 0, 1);
CALL aio_unify_field('time_tracker_category', 'sort', 'sort_order', '排序值，数值越小越靠前', 0, 1);
CALL aio_unify_field('task_detail', 'sort', 'sort_order', '排序值，数值越小越靠前', 0, 1);
CALL aio_unify_field('wardrobe_category', 'sort', 'sort_order', '排序值，数值越小越靠前', 0, 1);
CALL aio_unify_field('goal', 'pinned_sort', 'pinned_sort_order', '首页固定排序，数值越小越靠前', 0, 1);
CALL aio_unify_field('anniversary_record', 'pinned_sort', 'pinned_sort_order', '首页固定排序，数值越小越靠前', 0, 1);
CALL aio_unify_field('anniversary_record', 'note', 'remark', '备注，未填写时为空', 0, 1);
CALL aio_unify_field('membership_record', 'note', 'remark', '备注，未填写时为空', 0, 1);
CALL aio_unify_field('wardrobe_item', 'memo', 'remark', '备注，未填写时为空', 0, 1);
CALL aio_unify_field('life_footprint', 'note', 'remark', '备注，未填写时为空，应用限制5000字符', 0, 1);
CALL aio_unify_field('user_quick_nav', 'enabled', 'is_enabled', '是否启用：1=启用，0=停用', 0, 1);
CALL aio_unify_field('user_home_card_preference', 'enabled', 'is_enabled', '是否启用：1=启用，0=停用', 0, 1);
CALL aio_unify_field('notification_channel_config', 'enabled', 'is_enabled', '是否启用：1=启用，0=停用', 0, 1);
CALL aio_unify_field('notification_preference', 'enabled', 'is_enabled', '是否启用：1=启用，0=停用', 0, 1);
CALL aio_unify_field('sys_scheduled_job', 'enabled', 'is_enabled', '是否启用：1=启用，0=停用；不限制手动执行', 0, 1);
CALL aio_unify_field('sys_dict_type', 'status', 'is_enabled', '是否启用：1=启用，0=停用；NULL表示未设置', 1, 1);
CALL aio_unify_field('sys_dict_data', 'status', 'is_enabled', '是否启用：1=启用，0=停用；NULL表示未设置', 1, 1);
CALL aio_unify_field('user_dict_type', 'status', 'is_enabled', '是否启用：1=启用，0=停用；NULL表示未设置', 1, 1);
CALL aio_unify_field('user_dict_data', 'status', 'is_enabled', '是否启用：1=启用，0=停用；NULL表示未设置，个人覆盖项继承基础值', 1, 1);
CALL aio_unify_field('sys_menu', 'status', 'web_is_enabled', 'Web端是否启用：1=启用，0=停用', 0, 1);
CALL aio_unify_field('sys_menu', 'mobile_status', 'mobile_is_enabled', '移动端是否启用：1=启用，0=停用', 0, 1);

DROP PROCEDURE aio_unify_field;
SET SESSION sql_mode=@aio_previous_sql_mode;
