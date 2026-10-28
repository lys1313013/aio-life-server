-- 目的：收紧26张表的is_deleted为TINYINT NOT NULL DEFAULT 0、goal/b_video.user_id为非空，并补齐128处字段注释。
-- 兼容：MySQL 5.7.44、8.0；不依赖CHECK、ADD COLUMN IF NOT EXISTS或8.0专属元数据。
-- 前置：调用方选择目标库；以下清单中的44张InnoDB表、154个字段均已存在，字段基础类型符合清单。
-- 顺序：已有库执行适用的前序增量后执行；本脚本不依赖采集来源列，相关增量不得互相替代。
--       本脚本编号00，须在2026-10-28_02_unify_common_fields.sql之前执行；后者会重命名本清单中的enabled。
--       完成后续字段重命名后，本脚本原字段前置条件不再满足，不应作为整条迁移链的重跑入口。
--       本次已按用户要求同步全量初始化SQL；新库无需再执行本增量，重复执行也会跳过一致字段。
-- 影响：仅修改约束、26个删除字段的整数类型及指定注释；不增删列、索引、行，不回填任何业务数据。
-- 保留：其他列属性按目标库元数据重建，保留长度、精度、字符集、排序规则、默认值、自增及ON UPDATE。
--       不收紧公共分类/匿名日志的user_id；仅补注释的其他is_deleted保留原类型和可空性。
-- 数据检查：任何ALTER前检查全部目标列；26个删除标记必须非NULL且为0/1，两个归属字段不得为NULL。
--           检查包括软删除记录；异常时报告表、列并停止，不把NULL改成0，不猜测归属用户。
-- 执行：先暂停相关应用写入和定时任务，备份并核对目标库；使用支持DELIMITER的mysql客户端、utf8mb4连接。
--       必须遇错停止，禁止--force、并发执行或跳过预检；不要在持有业务事务的连接中运行。
-- 权限：information_schema读取、目标表SELECT/ALTER、CREATE TEMPORARY TABLES、CREATE/ALTER ROUTINE、EXECUTE。
-- 重跑：目标定义一致时不执行DDL；支持已完成部分表后修复问题并从头重跑。
-- 恢复：DDL隐式提交，不能靠ROLLBACK撤销；中断后核对已完成表，修正失败原因再重跑。恢复原约束需另行DDL。
--       _migrate_20261028_null_comments为本脚本专用过程；成功后删除，失败后下次运行会重建。
-- 验证：比较字段属性及COMMENT、索引和数据；检查NULL拒绝、0/1保留、已有用户归属及重复执行。

DROP PROCEDURE IF EXISTS `_migrate_20261028_null_comments`;
-- 过程固定使用严格模式，防止收紧类型/非空时发生静默转换；创建后立即恢复调用连接的原模式。
SET @aio_nc_saved_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ENGINE_SUBSTITUTION';
DELIMITER $$
CREATE PROCEDURE `_migrate_20261028_null_comments`()
BEGIN
  DECLARE finished BOOLEAN DEFAULT FALSE;
  DECLARE prepared BOOLEAN DEFAULT FALSE;
  DECLARE table_name_value VARCHAR(64);
  DECLARE column_name_value VARCHAR(64);
  DECLARE change_kind VARCHAR(16);
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
    SELECT s.table_name, s.column_name, s.change_kind, c.DATA_TYPE, c.COLUMN_TYPE,
           c.IS_NULLABLE, c.COLUMN_DEFAULT, c.EXTRA, c.CHARACTER_SET_NAME, c.COLLATION_NAME,
           c.COLUMN_COMMENT, s.target_comment
    FROM `_aio_nc_columns` s
    JOIN information_schema.COLUMNS c ON c.TABLE_SCHEMA=DATABASE()
      AND c.TABLE_NAME=s.table_name AND c.COLUMN_NAME=s.column_name
    ORDER BY s.table_name, c.ORDINAL_POSITION;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = TRUE;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    IF prepared THEN DEALLOCATE PREPARE aio_nc_stmt; END IF;
    DROP TEMPORARY TABLE IF EXISTS `_aio_nc_columns`;
    SET @aio_nc_sql = NULL;
    SET @aio_nc_invalid = NULL;
    RESIGNAL;
  END;

  IF DATABASE() IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Select the target database before running this migration';
  END IF;
  DROP TEMPORARY TABLE IF EXISTS `_aio_nc_columns`;
  CREATE TEMPORARY TABLE `_aio_nc_columns` (
    `table_name` VARCHAR(64) NOT NULL COMMENT '本次变更的业务表名',
    `column_name` VARCHAR(64) NOT NULL COMMENT '本次变更的字段名',
    `change_kind` VARCHAR(16) NOT NULL COMMENT 'COMMENT仅注释，DELETE_FLAG删除标记，OWNER用户归属',
    `expected_data_type` VARCHAR(64) NOT NULL COMMENT '预期基础类型，删除标记额外允许已迁移的tinyint',
    `target_comment` VARCHAR(1024) NOT NULL COMMENT '目标中文字段注释',
    -- 每个业务字段在本次变更中只能出现一次。
    PRIMARY KEY (`table_name`, `column_name`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='本次迁移的会话临时字段清单';
  INSERT INTO `_aio_nc_columns` (`table_name`, `column_name`, `change_kind`, `expected_data_type`, `target_comment`) VALUES
    ('user', 'id', 'COMMENT', 'bigint', '用户ID'),
    ('user', 'is_deleted', 'COMMENT', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('password_vault', 'is_deleted', 'DELETE_FLAG', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('user_secondary_lock_menu', 'is_deleted', 'DELETE_FLAG', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('user_quick_nav', 'is_deleted', 'DELETE_FLAG', 'int', '逻辑删除标记：0=未删除，1=已删除'),
    ('login_log', 'id', 'COMMENT', 'bigint', '登录日志ID'),
    ('sys_menu', 'is_deleted', 'DELETE_FLAG', 'int', '逻辑删除标记：0=未删除，1=已删除'),
    ('sys_dict_data', 'dict_id', 'COMMENT', 'bigint', '所属字典类型ID，关联sys_dict_type.dict_id'),
    ('system_config', 'is_deleted', 'DELETE_FLAG', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('feedback', 'is_deleted', 'DELETE_FLAG', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('feedback_comment', 'is_deleted', 'DELETE_FLAG', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('time_record', 'is_deleted', 'COMMENT', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('task', 'content', 'COMMENT', 'varchar', '任务标题，未填写时为空'),
    ('task', 'detail', 'COMMENT', 'varchar', '任务详细说明，未填写时为空'),
    ('task', 'column_id', 'COMMENT', 'bigint', '所属看板列ID，关联task_column.id；未分配时为空'),
    ('task', 'is_deleted', 'COMMENT', 'int', '逻辑删除标记：0=未删除，1=已删除'),
    ('task_column', 'bg_color', 'COMMENT', 'varchar', '看板列背景颜色，未设置时为空'),
    ('goal', 'user_id', 'OWNER', 'bigint', '所属用户ID，用于用户数据隔离'),
    ('goal', 'is_deleted', 'DELETE_FLAG', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('honor_category', 'is_deleted', 'DELETE_FLAG', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('honor_record', 'is_deleted', 'DELETE_FLAG', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('anniversary_record', 'is_deleted', 'DELETE_FLAG', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('memo', 'title', 'COMMENT', 'varchar', '备忘录标题，未填写时为空'),
    ('memo', 'is_deleted', 'DELETE_FLAG', 'int', '逻辑删除标记：0=未删除，1=已删除'),
    ('thought', 'id', 'COMMENT', 'bigint', '主键ID'),
    ('thought', 'create_user', 'COMMENT', 'bigint', '创建人用户ID'),
    ('thought', 'update_user', 'COMMENT', 'bigint', '最后更新人用户ID'),
    ('thought', 'is_deleted', 'DELETE_FLAG', 'int', '逻辑删除标记：0=未删除，1=已删除'),
    ('thought_rela_event', 'id', 'COMMENT', 'bigint', '主键ID'),
    ('thought_rela_event', 'thought_id', 'COMMENT', 'bigint', '所属闪念ID，关联thought.id'),
    ('thought_rela_event', 'content', 'COMMENT', 'text', '闪念关联事件内容'),
    ('thought_rela_event', 'create_user', 'COMMENT', 'bigint', '创建人用户ID'),
    ('thought_rela_event', 'update_user', 'COMMENT', 'bigint', '最后更新人用户ID'),
    ('thought_rela_event', 'is_deleted', 'DELETE_FLAG', 'int', '逻辑删除标记：0=未删除，1=已删除'),
    ('expense', 'is_deleted', 'DELETE_FLAG', 'int', '逻辑删除标记：0=未删除，1=已删除'),
    ('expense', 'exp_desc', 'COMMENT', 'varchar', '交易描述，未记录时为空'),
    ('expense', 'transaction_id', 'COMMENT', 'varchar', '支付平台交易号，保持原始字符串；未记录时为空'),
    ('expense', 'counterparty', 'COMMENT', 'varchar', '交易对方名称，未记录时为空'),
    ('expense', 'counterparty_acct', 'COMMENT', 'varchar', '交易对方账号，保持原始字符串；未记录时为空'),
    ('bank_card', 'is_deleted', 'COMMENT', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('bank_card', 'create_user', 'COMMENT', 'bigint', '创建人用户ID'),
    ('bank_card', 'create_time', 'COMMENT', 'datetime', '创建时间'),
    ('bank_card', 'update_user', 'COMMENT', 'bigint', '最后更新人用户ID'),
    ('bank_card', 'update_time', 'COMMENT', 'datetime', '最后更新时间'),
    ('bank_card_tag_rel', 'id', 'COMMENT', 'bigint', '主键ID'),
    ('bank_card_tag_rel', 'bank_card_id', 'COMMENT', 'bigint', '关联银行卡ID，关联bank_card.id'),
    ('bank_card_tag_rel', 'is_deleted', 'COMMENT', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('bank_card_tag_rel', 'create_user', 'COMMENT', 'bigint', '创建人用户ID'),
    ('bank_card_tag_rel', 'create_time', 'COMMENT', 'datetime', '创建时间'),
    ('bank_card_tag_rel', 'update_user', 'COMMENT', 'bigint', '最后更新人用户ID'),
    ('bank_card_tag_rel', 'update_time', 'COMMENT', 'datetime', '最后更新时间'),
    ('income', 'is_deleted', 'DELETE_FLAG', 'int', '逻辑删除标记：0=未删除，1=已删除'),
    ('income', 'tax', 'COMMENT', 'decimal', '收入税额，单位元；NULL表示未记录，0表示零税额'),
    ('exercise_record', 'exercise_count', 'COMMENT', 'int', '运动次数，单位次；0表示本条未记录有效次数'),
    ('read_record', 'is_deleted', 'DELETE_FLAG', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('movie', 'is_deleted', 'DELETE_FLAG', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('b_video', 'cover_file_id', 'COMMENT', 'varchar', '当前封面附件ID，关联file.id；尚无内部封面时为空'),
    ('b_video', 'cover_state', 'COMMENT', 'varchar', '封面状态：NONE=无来源，PENDING=导入中，READY=可用，FAILED=失败'),
    ('b_video', 'cover_version', 'COMMENT', 'bigint', '封面导入版本，从0开始，每次重新入队递增，防止旧任务覆盖新封面'),
    ('b_video', 'owner_name', 'COMMENT', 'varchar', '视频UP主名称，未获取时为空'),
    ('b_video', 'user_id', 'OWNER', 'bigint', '所属用户ID，用于用户数据隔离'),
    ('b_video', 'is_deleted', 'DELETE_FLAG', 'int', '逻辑删除标记：0=未删除，1=已删除'),
    ('performance', 'performer', 'COMMENT', 'varchar', '演出者或团体名称，未填写时为空'),
    ('performance', 'is_deleted', 'DELETE_FLAG', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('membership_record', 'is_deleted', 'DELETE_FLAG', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('notification_channel_config', 'create_user', 'COMMENT', 'bigint', '创建人用户ID'),
    ('notification_channel_config', 'create_time', 'COMMENT', 'datetime', '创建时间'),
    ('notification_channel_config', 'update_user', 'COMMENT', 'bigint', '最后更新人用户ID'),
    ('notification_channel_config', 'update_time', 'COMMENT', 'datetime', '最后更新时间'),
    ('notification_channel_config', 'is_deleted', 'COMMENT', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('notification_preference', 'user_id', 'COMMENT', 'bigint', '所属用户ID，用于用户数据隔离'),
    ('notification_preference', 'biz_type', 'COMMENT', 'varchar', '通知业务类型：LEETCODE_REMINDER=未打卡提醒，LEETCODE_DAILY=每日一题，FEEDBACK_ADMIN=新反馈，FEEDBACK_REPLY=反馈回复'),
    ('notification_preference', 'enabled', 'COMMENT', 'tinyint', '是否启用该业务渠道通知：0=关闭，1=启用'),
    ('notification_preference', 'create_user', 'COMMENT', 'bigint', '创建人用户ID'),
    ('notification_preference', 'create_time', 'COMMENT', 'datetime', '创建时间'),
    ('notification_preference', 'update_user', 'COMMENT', 'bigint', '最后更新人用户ID'),
    ('notification_preference', 'update_time', 'COMMENT', 'datetime', '最后更新时间'),
    ('notification_preference', 'is_deleted', 'COMMENT', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('notification_delivery', 'dedup_key', 'COMMENT', 'varchar', '通知事件去重标识，与用户ID和渠道共同确定同一次投递'),
    ('notification_delivery', 'user_id', 'COMMENT', 'bigint', '所属用户ID，用于用户数据隔离'),
    ('notification_delivery', 'biz_type', 'COMMENT', 'varchar', '通知业务类型：LEETCODE_REMINDER=未打卡提醒，LEETCODE_DAILY=每日一题，FEEDBACK_ADMIN=新反馈，FEEDBACK_REPLY=反馈回复'),
    ('notification_delivery', 'channel', 'COMMENT', 'varchar', '投递渠道，当前持久化重试由FEISHU飞书渠道使用'),
    ('notification_delivery', 'retry_count', 'COMMENT', 'int', '累计发送失败次数，初始0，每次失败递增'),
    ('notification_delivery', 'next_retry_time', 'COMMENT', 'datetime', '下次重试时间，未安排重试时为空'),
    ('notification_delivery', 'provider_code', 'COMMENT', 'varchar', '最近渠道返回码或内部失败码，尚未返回时为空'),
    ('notification_delivery', 'error_message', 'COMMENT', 'varchar', '最近投递失败原因，成功或尚未失败时为空'),
    ('notification_delivery', 'create_user', 'COMMENT', 'bigint', '创建人用户ID'),
    ('notification_delivery', 'create_time', 'COMMENT', 'datetime', '创建时间'),
    ('notification_delivery', 'update_user', 'COMMENT', 'bigint', '最后更新人用户ID'),
    ('notification_delivery', 'update_time', 'COMMENT', 'datetime', '最后更新时间'),
    ('notification_delivery', 'is_deleted', 'COMMENT', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('wardrobe_category', 'is_deleted', 'DELETE_FLAG', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('wardrobe_item', 'is_deleted', 'DELETE_FLAG', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('mbti_result', 'is_deleted', 'DELETE_FLAG', 'int', '逻辑删除标记：0=未删除，1=已删除'),
    ('cbti_personality', 'is_deleted', 'DELETE_FLAG', 'int', '逻辑删除标记：0=未删除，1=已删除'),
    ('cbti_result', 'is_deleted', 'DELETE_FLAG', 'int', '逻辑删除标记：0=未删除，1=已删除'),
    ('file', 'storage_object_id', 'COMMENT', 'bigint', '关联不可变图片对象ID；普通上传未接入对象表时为空'),
    ('bank_card_cover_template', 'sort_order', 'COMMENT', 'int', '排序值，数值越小越靠前'),
    ('bank_card_cover_template', 'is_deleted', 'COMMENT', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('bank_card_cover_template', 'create_user', 'COMMENT', 'bigint', '创建人用户ID'),
    ('bank_card_cover_template', 'create_time', 'COMMENT', 'datetime', '创建时间'),
    ('bank_card_cover_template', 'update_user', 'COMMENT', 'bigint', '最后更新人用户ID'),
    ('bank_card_cover_template', 'update_time', 'COMMENT', 'datetime', '最后更新时间'),
    ('storage_object', 'id', 'COMMENT', 'bigint', '主键ID'),
    ('storage_object', 'bucket', 'COMMENT', 'varchar', '对象存储桶名称，视频封面使用system'),
    ('storage_object', 'object_key', 'COMMENT', 'varchar', '桶内完整对象键，视频封面由bvedio前缀、SHA-256和扩展名组成'),
    ('storage_object', 'sha256', 'COMMENT', 'char', '最终存储文件字节的SHA-256摘要，64位小写十六进制'),
    ('storage_object', 'file_size', 'COMMENT', 'bigint', '文件大小，单位字节'),
    ('storage_object', 'content_type', 'COMMENT', 'varchar', '实际存储文件的MIME类型'),
    ('storage_object', 'create_user', 'COMMENT', 'bigint', '创建人用户ID'),
    ('storage_object', 'create_time', 'COMMENT', 'datetime', '创建时间'),
    ('storage_object', 'update_user', 'COMMENT', 'bigint', '最后更新人用户ID'),
    ('storage_object', 'update_time', 'COMMENT', 'datetime', '最后更新时间'),
    ('storage_object', 'is_deleted', 'COMMENT', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('image_import_task', 'id', 'COMMENT', 'bigint', '主键ID'),
    ('image_import_task', 'video_id', 'COMMENT', 'bigint', '关联视频ID，关联b_video.id'),
    ('image_import_task', 'cover_version', 'COMMENT', 'bigint', '入队时的封面版本，须与视频当前版本一致才可发布'),
    ('image_import_task', 'source_url', 'COMMENT', 'varchar', '本次任务抓取的原始封面URL'),
    ('image_import_task', 'state', 'COMMENT', 'varchar', '任务状态：PENDING=待执行，RUNNING=执行中，READY=完成，FAILED=失败，CANCELLED=已取消'),
    ('image_import_task', 'attempts', 'COMMENT', 'int', '领取执行次数，初始0，每次领取递增至最多5'),
    ('image_import_task', 'next_attempt_at', 'COMMENT', 'datetime', '首次可执行或退避后的下次重试时间'),
    ('image_import_task', 'lease_token', 'COMMENT', 'varchar', '本次执行租约令牌，未领取或已释放时为空'),
    ('image_import_task', 'lease_until', 'COMMENT', 'datetime', '执行租约到期时间，未领取或已释放时为空'),
    ('image_import_task', 'error_code', 'COMMENT', 'varchar', '最近一次失败码，重试成功后保留；尚未失败时为空'),
    ('image_import_task', 'create_user', 'COMMENT', 'bigint', '创建人用户ID'),
    ('image_import_task', 'create_time', 'COMMENT', 'datetime', '创建时间'),
    ('image_import_task', 'update_user', 'COMMENT', 'bigint', '最后更新人用户ID'),
    ('image_import_task', 'update_time', 'COMMENT', 'datetime', '最后更新时间'),
    ('image_import_task', 'is_deleted', 'COMMENT', 'tinyint', '逻辑删除标记：0=未删除，1=已删除'),
    ('user_home_card_preference', 'id', 'COMMENT', 'bigint', '主键ID'),
    ('user_home_card_preference', 'user_id', 'COMMENT', 'bigint', '所属用户ID，用于用户数据隔离'),
    ('user_home_card_preference', 'card_key', 'COMMENT', 'varchar', '首页卡片注册清单中的稳定标识'),
    ('user_home_card_preference', 'enabled', 'COMMENT', 'tinyint', '是否显示首页卡片：0=隐藏，1=显示'),
    ('user_home_card_preference', 'sort_order', 'COMMENT', 'int', '首页卡片分组内排序，数值越小越靠前'),
    ('user_home_card_preference', 'create_user', 'COMMENT', 'bigint', '创建人用户ID'),
    ('user_home_card_preference', 'create_time', 'COMMENT', 'datetime', '创建时间'),
    ('user_home_card_preference', 'update_user', 'COMMENT', 'bigint', '最后更新人用户ID'),
    ('user_home_card_preference', 'update_time', 'COMMENT', 'datetime', '最后更新时间'),
    ('user_home_card_preference', 'is_deleted', 'COMMENT', 'int', '统一保留的删除标记，固定为0；恢复默认使用物理删除'),
    ('user_body_profile', 'id', 'COMMENT', 'bigint', '主键ID'),
    ('user_body_profile', 'user_id', 'COMMENT', 'bigint', '所属用户ID，用于用户数据隔离'),
    ('user_body_profile', 'version', 'COMMENT', 'int', '乐观锁版本，首次保存为1，每次成功修改递增'),
    ('user_body_profile', 'gender', 'COMMENT', 'varchar', '性别：male=男，female=女；未填写时为空'),
    ('user_body_profile', 'measurement_side', 'COMMENT', 'varchar', '四肢围度测量侧别：left=左侧，right=右侧；未填写时为空'),
    ('user_body_profile', 'arm_state', 'COMMENT', 'varchar', '上臂测量状态：relaxed=放松，flexed=屈臂；未填写时为空'),
    ('user_body_profile', 'shoe_system', 'COMMENT', 'varchar', '鞋码制式：EU=欧码，CN=中国码，US=美码，UK=英码；未填写时为空'),
    ('user_body_profile', 'top_size', 'COMMENT', 'varchar', '上装尺码，自由文本；未填写时为空'),
    ('user_body_profile', 'trousers_size', 'COMMENT', 'varchar', '裤装尺码，自由文本；未填写时为空'),
    ('user_body_profile', 'shoe_size', 'COMMENT', 'varchar', '鞋码文本，按shoe_system解释；未填写时为空'),
    ('user_body_profile', 'create_user', 'COMMENT', 'bigint', '创建人用户ID'),
    ('user_body_profile', 'create_time', 'COMMENT', 'datetime', '创建时间'),
    ('user_body_profile', 'update_user', 'COMMENT', 'bigint', '最后更新人用户ID'),
    ('user_body_profile', 'update_time', 'COMMENT', 'datetime', '最后更新时间'),
    ('user_body_profile', 'is_deleted', 'COMMENT', 'int', '逻辑删除标记：0=未删除，1=已删除');

  -- 第一阶段完整检查结构；此时不修改任何业务表。
  SELECT MIN(CONCAT(s.table_name, '.', s.column_name)) INTO problem
  FROM `_aio_nc_columns` s
  LEFT JOIN information_schema.TABLES t ON t.TABLE_SCHEMA=DATABASE() AND t.TABLE_NAME=s.table_name
  LEFT JOIN information_schema.COLUMNS c ON c.TABLE_SCHEMA=DATABASE()
    AND c.TABLE_NAME=s.table_name AND c.COLUMN_NAME=s.column_name
  WHERE t.TABLE_NAME IS NULL OR t.TABLE_TYPE<>'BASE TABLE' OR t.ENGINE<>'InnoDB' OR c.COLUMN_NAME IS NULL;
  IF problem IS NOT NULL THEN
    SET problem=CONCAT('Missing column or non-InnoDB base table: ', problem);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=problem;
  END IF;

  SELECT MIN(CONCAT(s.table_name, '.', s.column_name)) INTO problem
  FROM `_aio_nc_columns` s
  JOIN information_schema.COLUMNS c ON c.TABLE_SCHEMA=DATABASE()
    AND c.TABLE_NAME=s.table_name AND c.COLUMN_NAME=s.column_name
  WHERE (c.DATA_TYPE<>s.expected_data_type AND NOT (s.change_kind='DELETE_FLAG' AND c.DATA_TYPE='tinyint'))
    OR COALESCE(c.GENERATION_EXPRESSION, '')<>''
    OR NOT (TRIM(REPLACE(LOWER(c.EXTRA), 'default_generated', '')) IN ('', 'auto_increment')
      OR TRIM(REPLACE(LOWER(c.EXTRA), 'default_generated', '')) REGEXP '^on update current_timestamp([(][0-6]?[)])?$')
    OR (LOWER(c.EXTRA) LIKE '%default_generated%'
      AND NOT (c.DATA_TYPE IN ('timestamp','datetime')
        AND LOWER(COALESCE(c.COLUMN_DEFAULT,'')) REGEXP '^current_timestamp([(][0-6]?[)])?$'));
  IF problem IS NOT NULL THEN
    SET problem=CONCAT('Unsupported column definition: ', problem, '; inspect SHOW CREATE TABLE');
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=problem;
  END IF;

  -- 第二阶段检查所有待收紧列的数据；任何异常均在首个业务ALTER之前报错。
  OPEN target_columns;
  data_checks: LOOP
    FETCH target_columns INTO table_name_value, column_name_value, change_kind, data_type_value,
      column_type_value, nullable_value, default_value, extra_value, charset_value,
      collation_value, comment_value, target_comment;
    IF finished THEN LEAVE data_checks; END IF;
    IF change_kind<>'COMMENT' THEN
      SET @aio_nc_sql=CONCAT('SELECT EXISTS(SELECT 1 FROM `',table_name_value,'` WHERE `',column_name_value,'` IS NULL',
        IF(change_kind='DELETE_FLAG',CONCAT(' OR `',column_name_value,'` NOT IN (0,1)'),''),
        ' LIMIT 1) INTO @aio_nc_invalid');
      PREPARE aio_nc_stmt FROM @aio_nc_sql;
      SET prepared=TRUE;
      EXECUTE aio_nc_stmt;
      DEALLOCATE PREPARE aio_nc_stmt;
      SET prepared=FALSE;
      IF @aio_nc_invalid=1 THEN
        SET problem=CONCAT('Invalid data in ',table_name_value,'.',column_name_value,
          IF(change_kind='DELETE_FLAG',': NULL or outside 0/1; no ALTER applied',': NULL owner; no ALTER applied'));
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=problem;
      END IF;
    END IF;
  END LOOP;
  CLOSE target_columns;

  -- 第三阶段按表合并MODIFY；仅改指定约束与注释，其他属性来自目标库元数据。
  SET finished=FALSE;
  OPEN target_columns;
  apply_changes: LOOP
    FETCH target_columns INTO table_name_value, column_name_value, change_kind, data_type_value,
      column_type_value, nullable_value, default_value, extra_value, charset_value,
      collation_value, comment_value, target_comment;
    IF finished OR (current_table<>'' AND current_table<>table_name_value) THEN
      IF changes<>'' THEN
        SET @aio_nc_sql=CONCAT('ALTER TABLE `',current_table,'` ',changes);
        PREPARE aio_nc_stmt FROM @aio_nc_sql;
        SET prepared=TRUE;
        EXECUTE aio_nc_stmt;
        DEALLOCATE PREPARE aio_nc_stmt;
        SET prepared=FALSE;
      END IF;
      SET changes='';
    END IF;
    IF finished THEN LEAVE apply_changes; END IF;
    SET current_table=table_name_value;
    IF BINARY comment_value<>BINARY target_comment
       OR (change_kind='DELETE_FLAG' AND (column_type_value NOT IN ('tinyint','tinyint(4)')
         OR nullable_value<>'NO' OR NOT (default_value <=> '0')))
       OR (change_kind='OWNER' AND nullable_value<>'NO') THEN
      IF change_kind='DELETE_FLAG' THEN
        SET column_type_value='TINYINT';
        SET default_value='0';
      END IF;
      IF change_kind<>'COMMENT' THEN SET nullable_value='NO'; END IF;
      SET extra_value=TRIM(REPLACE(LOWER(extra_value),'default_generated',''));
      SET definition_value=CONCAT('MODIFY COLUMN `',column_name_value,'` ',column_type_value,
        IF(charset_value IS NULL,'',CONCAT(' CHARACTER SET ',charset_value,' COLLATE ',collation_value)),
        IF(nullable_value='YES',' NULL',' NOT NULL'));
      IF default_value IS NOT NULL THEN
        SET definition_value=CONCAT(definition_value,' DEFAULT ',
          IF(data_type_value IN ('datetime','timestamp')
            AND LOWER(default_value) REGEXP '^current_timestamp([(][0-6]?[)])?$',default_value,QUOTE(default_value)));
      ELSEIF nullable_value='YES' THEN
        SET definition_value=CONCAT(definition_value,' DEFAULT NULL');
      END IF;
      SET definition_value=CONCAT(definition_value,
        IF(extra_value='','',CONCAT(' ',extra_value)),' COMMENT ',QUOTE(target_comment));
      SET changes=CONCAT(changes,IF(changes='','',', '),definition_value);
    END IF;
  END LOOP;
  CLOSE target_columns;
  DROP TEMPORARY TABLE `_aio_nc_columns`;
  SET @aio_nc_sql=NULL;
  SET @aio_nc_invalid=NULL;
END$$
DELIMITER ;
SET SESSION sql_mode = @aio_nc_saved_sql_mode;
SET @aio_nc_saved_sql_mode = NULL;
CALL `_migrate_20261028_null_comments`();
DROP PROCEDURE `_migrate_20261028_null_comments`;
