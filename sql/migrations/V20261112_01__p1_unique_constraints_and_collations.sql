-- 目的：补齐业务唯一键，并把区分大小写的外部标识符改为确定性排序。
-- 前置：已执行 V20261110_01、V20261110_02_001 至 V20261110_02_011、V20261111_01；数据库为 PostgreSQL 18+。
-- 影响：只新增或替换索引、修改列排序规则，不改写业务数据；存在有效重复记录时中止并提示先清理。
-- 重跑：由 Flyway 版本历史保证只执行一次；失败事务回滚后修正原因再迁移。
-- 执行：通过 mvn -f tools/flyway/pom.xml compile flyway:migrate 执行，遇错停止。

-- 唯一约束建立前先暴露存量重复，避免迁移在 CREATE INDEX 处只给出索引级错误。
DO $$
DECLARE
  duplicate_groups bigint;
BEGIN
  SELECT count(*) INTO duplicate_groups FROM (
    SELECT username FROM "user" WHERE is_deleted = 0
    GROUP BY username HAVING count(*) > 1
  ) duplicated;
  IF duplicate_groups > 0 THEN
    RAISE EXCEPTION 'user.username 存在 % 组有效重复记录，请先处理后再执行迁移', duplicate_groups;
  END IF;

  SELECT count(*) INTO duplicate_groups FROM (
    SELECT email FROM "user" WHERE is_deleted = 0 AND email IS NOT NULL AND email <> ''
    GROUP BY email HAVING count(*) > 1
  ) duplicated;
  IF duplicate_groups > 0 THEN
    RAISE EXCEPTION 'user.email 存在 % 组有效重复记录，请先处理后再执行迁移', duplicate_groups;
  END IF;

  SELECT count(*) INTO duplicate_groups FROM (
    SELECT user_id, platform FROM user_bind WHERE is_deleted = 0
    GROUP BY user_id, platform HAVING count(*) > 1
  ) duplicated;
  IF duplicate_groups > 0 THEN
    RAISE EXCEPTION 'user_bind(user_id, platform) 存在 % 组有效重复记录，请先处理后再执行迁移', duplicate_groups;
  END IF;

  SELECT count(*) INTO duplicate_groups FROM (
    SELECT user_id, channel FROM notification_channel_config WHERE is_deleted = 0
    GROUP BY user_id, channel HAVING count(*) > 1
  ) duplicated;
  IF duplicate_groups > 0 THEN
    RAISE EXCEPTION 'notification_channel_config(user_id, channel) 存在 % 组有效重复记录，请先处理后再执行迁移', duplicate_groups;
  END IF;

  SELECT count(*) INTO duplicate_groups FROM (
    SELECT user_id, biz_type, channel FROM notification_preference WHERE is_deleted = 0
    GROUP BY user_id, biz_type, channel HAVING count(*) > 1
  ) duplicated;
  IF duplicate_groups > 0 THEN
    RAISE EXCEPTION 'notification_preference(user_id, biz_type, channel) 存在 % 组有效重复记录，请先处理后再执行迁移', duplicate_groups;
  END IF;

  SELECT count(*) INTO duplicate_groups FROM (
    SELECT api_key COLLATE "C" FROM api_key WHERE is_deleted = 0
    GROUP BY api_key COLLATE "C" HAVING count(*) > 1
  ) duplicated;
  IF duplicate_groups > 0 THEN
    RAISE EXCEPTION 'api_key.api_key 存在 % 组有效重复记录，请先处理后再执行迁移', duplicate_groups;
  END IF;

  SELECT count(*) INTO duplicate_groups FROM (
    SELECT config_key FROM system_config WHERE is_deleted = 0
    GROUP BY config_key HAVING count(*) > 1
  ) duplicated;
  IF duplicate_groups > 0 THEN
    RAISE EXCEPTION 'system_config.config_key 存在 % 组有效重复记录，请先处理后再执行迁移', duplicate_groups;
  END IF;

  SELECT count(*) INTO duplicate_groups FROM (
    SELECT user_id, bvid COLLATE "C" FROM b_video WHERE is_deleted = 0 AND bvid IS NOT NULL AND bvid <> ''
    GROUP BY user_id, bvid COLLATE "C" HAVING count(*) > 1
  ) duplicated;
  IF duplicate_groups > 0 THEN
    RAISE EXCEPTION 'b_video(user_id, bvid) 存在 % 组有效重复记录，请先处理后再执行迁移', duplicate_groups;
  END IF;
END
$$;

-- 密钥、对象键、外部订单号与外部标识区分大小写，使用确定性排序；展示文本继续使用 aio_unicode_ci。
ALTER TABLE api_key ALTER COLUMN api_key TYPE varchar(128) COLLATE "C";
ALTER TABLE b_video ALTER COLUMN bvid TYPE varchar(20) COLLATE "C";
ALTER TABLE b_video ALTER COLUMN url TYPE varchar(1000) COLLATE "C";
ALTER TABLE file ALTER COLUMN file_name TYPE varchar(255) COLLATE "C";
ALTER TABLE storage_object ALTER COLUMN object_key TYPE varchar(255) COLLATE "C";
ALTER TABLE data_export_job ALTER COLUMN object_key TYPE varchar(255) COLLATE "C";
ALTER TABLE expense ALTER COLUMN transaction_id TYPE varchar(100) COLLATE "C";
ALTER TABLE expense ALTER COLUMN merchant_order_no TYPE varchar(100) COLLATE "C";
ALTER TABLE expense ALTER COLUMN counterparty_acct TYPE varchar(100) COLLATE "C";
ALTER TABLE performance ALTER COLUMN order_number TYPE varchar(50) COLLATE "C";
ALTER TABLE device ALTER COLUMN source_order_id TYPE varchar(100) COLLATE "C";
ALTER TABLE device ALTER COLUMN source_product_id TYPE varchar(100) COLLATE "C";
ALTER TABLE device ALTER COLUMN source_item_id TYPE varchar(128) COLLATE "C";
ALTER TABLE wardrobe_item ALTER COLUMN source_order_id TYPE varchar(100) COLLATE "C";
ALTER TABLE wardrobe_item ALTER COLUMN source_product_id TYPE varchar(100) COLLATE "C";
ALTER TABLE wardrobe_item ALTER COLUMN source_item_id TYPE varchar(128) COLLATE "C";
ALTER TABLE bank_card ALTER COLUMN card_no_ciphertext TYPE varchar(512) COLLATE "C";

-- 有效记录唯一；已逻辑删除的记录不占用业务键。
CREATE UNIQUE INDEX uk_user_username_active ON "user" (username) WHERE is_deleted = 0;
COMMENT ON INDEX uk_user_username_active IS '有效用户的用户名唯一，已删除用户不占用';

CREATE UNIQUE INDEX uk_user_email_active ON "user" (email) WHERE is_deleted = 0 AND email IS NOT NULL AND email <> '';
COMMENT ON INDEX uk_user_email_active IS '有效用户的邮箱唯一，空邮箱不参与比较，已删除用户不占用';

CREATE UNIQUE INDEX uk_user_bind_user_platform ON user_bind (user_id, platform) WHERE is_deleted = 0;
COMMENT ON INDEX uk_user_bind_user_platform IS '同一用户在同一平台只能有一条有效绑定';

CREATE UNIQUE INDEX uk_notification_channel_config_user_channel ON notification_channel_config (user_id, channel) WHERE is_deleted = 0;
COMMENT ON INDEX uk_notification_channel_config_user_channel IS '同一用户同一通知渠道只能有一条有效配置';

CREATE UNIQUE INDEX uk_notification_preference_user_biz_channel ON notification_preference (user_id, biz_type, channel) WHERE is_deleted = 0;
COMMENT ON INDEX uk_notification_preference_user_biz_channel IS '同一用户同一业务类型和渠道只能有一条有效偏好';

CREATE UNIQUE INDEX uk_api_key_api_key ON api_key (api_key) WHERE is_deleted = 0;
COMMENT ON INDEX uk_api_key_api_key IS '有效 API Key 精确唯一，已删除 Key 可重新使用';

CREATE UNIQUE INDEX uk_system_config_config_key ON system_config (config_key) WHERE is_deleted = 0;
COMMENT ON INDEX uk_system_config_config_key IS '有效系统配置键唯一，已删除配置不占用';

CREATE UNIQUE INDEX uk_b_video_user_bvid ON b_video (user_id, bvid) WHERE is_deleted = 0 AND bvid IS NOT NULL AND bvid <> '';
COMMENT ON INDEX uk_b_video_user_bvid IS '同一用户同一 BV 号只能有一条有效视频记录，未记录 BV 号时为空不参与比较';

-- 已使用逻辑删除的业务键改为部分唯一索引，删除后可以重新创建同名对象。
DROP INDEX sys_menu_uk_sys_menu_path;
CREATE UNIQUE INDEX sys_menu_uk_sys_menu_path ON sys_menu (path) WHERE is_deleted = 0;
COMMENT ON INDEX sys_menu_uk_sys_menu_path IS '有效菜单路由路径唯一，已删除菜单不占用';

DROP INDEX sys_scheduled_job_uk_sys_scheduled_job_code;
CREATE UNIQUE INDEX sys_scheduled_job_uk_sys_scheduled_job_code ON sys_scheduled_job (job_code) WHERE is_deleted = 0;
COMMENT ON INDEX sys_scheduled_job_uk_sys_scheduled_job_code IS '有效定时任务编码唯一，已删除任务不占用';

DROP INDEX cbti_personality_uk_cbti_personality_code;
CREATE UNIQUE INDEX cbti_personality_uk_cbti_personality_code ON cbti_personality (code) WHERE is_deleted = 0;
COMMENT ON INDEX cbti_personality_uk_cbti_personality_code IS '有效人格代码唯一，已删除人格不占用';

DROP INDEX system_region_uk_system_region_code;
CREATE UNIQUE INDEX system_region_uk_system_region_code ON system_region (code) WHERE is_deleted = 0;
COMMENT ON INDEX system_region_uk_system_region_code IS '有效地区编码唯一，已删除版本记录不占用';

DROP INDEX data_export_job_uk_data_export_job_request;
CREATE UNIQUE INDEX data_export_job_uk_data_export_job_request ON data_export_job (user_id, request_key) WHERE is_deleted = 0;
COMMENT ON INDEX data_export_job_uk_data_export_job_request IS '同一用户有效导出任务的幂等键唯一，已删除任务不占用';
