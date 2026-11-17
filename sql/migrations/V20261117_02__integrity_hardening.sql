-- 目的：补齐 0/1 状态列校验和业务键唯一约束，清理附件表冗余索引，放宽邮箱长度并把 MBTI 原始结果改为 jsonb。
-- 前置：PostgreSQL 18+；已执行 V20261117_01；执行前停写并备份数据库。
-- 影响：只收紧约束、调整列类型和索引；不删除业务行、不回填业务值。存在非 0/1 状态或重复交易号时迁移整体失败，需先人工核对。
-- 重跑：Flyway 只执行一次；全部 DDL 在同一事务执行，失败整体回滚，修正后对同一版本重新执行。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；遇错停止。

-- 锁等待超过 10 秒时让本次迁移失败回滚，避免与应用事务形成死锁。
SET LOCAL lock_timeout = '10s';

-- 导出任务按 coordinator -> job 的顺序持锁；提前取得 coordinator 锁，
-- 避免迁移先修改 job 列类型后与导出事务形成锁顺序死锁。
LOCK TABLE data_export_coordinator IN ACCESS EXCLUSIVE MODE;

-- 1. 业务键唯一：先确认没有重复，再建部分唯一索引。
DO $$
DECLARE
    duplicate_groups bigint;
BEGIN
    SELECT count(*) INTO duplicate_groups
      FROM (
        SELECT 1
          FROM expense
         WHERE is_deleted = 0
           AND transaction_id IS NOT NULL
           AND btrim(transaction_id) <> ''
         GROUP BY user_id, transaction_id
        HAVING count(*) > 1
      ) duplicates;
    IF duplicate_groups > 0 THEN
        RAISE EXCEPTION 'expense 存在 % 组同一用户的重复交易号，请先人工合并后再迁移', duplicate_groups;
    END IF;
END $$;

CREATE UNIQUE INDEX uk_expense_user_transaction
    ON expense (user_id, transaction_id)
 WHERE is_deleted = 0 AND transaction_id IS NOT NULL AND btrim(transaction_id) <> '';
COMMENT ON INDEX uk_expense_user_transaction IS '同一用户同一支付平台交易号只能有一条有效支出；空交易号不参与比较';

-- 2. 附件：单一用途唯一索引已覆盖旧索引；删除迁移前谓词残留的失效索引并统一索引名。
DROP INDEX IF EXISTS uk_file_b_video_cover;
DROP INDEX IF EXISTS uk_file_user_avatar;
DROP INDEX IF EXISTS uk_file_record_image;
ALTER INDEX file_idx_biz RENAME TO idx_attachment_biz;
ALTER INDEX file_idx_file_storage_object RENAME TO idx_attachment_storage_object;
COMMENT ON INDEX idx_attachment_biz IS '按业务用途与业务记录检索附件';
COMMENT ON INDEX idx_attachment_storage_object IS '按物理对象反查附件引用';

-- 3. 整数状态收窄为 smallint；先拒绝超出 0/1 的已有值。
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM data_export_grant WHERE consumed NOT IN (0, 1)) THEN
        RAISE EXCEPTION 'data_export_grant.consumed 存在非 0/1 值，请先人工核对';
    END IF;
    IF EXISTS (SELECT 1 FROM data_export_job WHERE cancel_requested NOT IN (0, 1) OR quota_charged NOT IN (0, 1)) THEN
        RAISE EXCEPTION 'data_export_job 存在非 0/1 的完成标记，请先人工核对';
    END IF;
    IF EXISTS (SELECT 1 FROM message WHERE type NOT IN (0, 1)) THEN
        RAISE EXCEPTION 'message.type 存在非 0/1 值，请先人工核对';
    END IF;
    IF EXISTS (SELECT 1 FROM task_detail WHERE is_starred NOT IN (0, 1)) THEN
        RAISE EXCEPTION 'task_detail.is_starred 存在非 0/1 值，请先人工核对';
    END IF;
END $$;

ALTER TABLE data_export_grant ALTER COLUMN consumed TYPE smallint USING consumed::smallint;
ALTER TABLE data_export_job ALTER COLUMN cancel_requested TYPE smallint USING cancel_requested::smallint;
ALTER TABLE data_export_job ALTER COLUMN quota_charged TYPE smallint USING quota_charged::smallint;
ALTER TABLE message ALTER COLUMN type TYPE smallint USING type::smallint;
ALTER TABLE task_detail ALTER COLUMN is_starred TYPE smallint USING is_starred::smallint;

-- 4. 邮箱按标准最大长度放宽；应用 DTO 同步限制 254 字符。
ALTER TABLE sys_user ALTER COLUMN email TYPE varchar(254);
COMMENT ON COLUMN sys_user.email IS '邮箱，最长254字符';
ALTER TABLE mail_log ALTER COLUMN send_to TYPE varchar(254);
COMMENT ON COLUMN mail_log.send_to IS '接收者邮箱，最长254字符';

-- 5. MBTI 原始结果统一为 jsonb；历史空串转 NULL，非法 JSON 保守包成字符串，不丢原文。
ALTER TABLE mbti_result
    ALTER COLUMN raw_result TYPE jsonb
    USING (
        CASE
            WHEN raw_result IS NULL OR btrim(raw_result) = '' THEN NULL
            WHEN pg_input_is_valid(raw_result, 'jsonb') THEN raw_result::jsonb
            ELSE to_jsonb(raw_result)
        END
    );
COMMENT ON COLUMN mbti_result.raw_result IS '原始结果数据（JSON 对象）；历史空串为 NULL，非法 JSON 文本按字符串保留';

-- 6. 全部 0/1 状态列补齐数据库校验，避免部分唯一索引与逻辑删除出现第三态。
ALTER TABLE anniversary_record ADD CONSTRAINT ck_anniversary_record_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE anniversary_record ADD CONSTRAINT ck_anniversary_record_is_pinned CHECK (is_pinned IN (0, 1));
ALTER TABLE api_key ADD CONSTRAINT ck_api_key_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE attachment ADD CONSTRAINT ck_attachment_is_public CHECK (is_public IN (0, 1));
ALTER TABLE b_video ADD CONSTRAINT ck_b_video_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE bank_card ADD CONSTRAINT ck_bank_card_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE bank_card_cover_template ADD CONSTRAINT ck_bank_card_cover_template_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE bank_card_tag_rel ADD CONSTRAINT ck_bank_card_tag_rel_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE cbti_personality ADD CONSTRAINT ck_cbti_personality_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE cbti_personality ADD CONSTRAINT ck_cbti_personality_is_special CHECK (is_special IN (0, 1));
ALTER TABLE cbti_result ADD CONSTRAINT ck_cbti_result_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE data_export_audit_event ADD CONSTRAINT ck_data_export_audit_event_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE data_export_coordinator ADD CONSTRAINT ck_data_export_coordinator_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE data_export_grant ADD CONSTRAINT ck_data_export_grant_consumed CHECK (consumed IN (0, 1));
ALTER TABLE data_export_grant ADD CONSTRAINT ck_data_export_grant_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE data_export_job ADD CONSTRAINT ck_data_export_job_cancel_requested CHECK (cancel_requested IN (0, 1));
ALTER TABLE data_export_job ADD CONSTRAINT ck_data_export_job_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE data_export_job ADD CONSTRAINT ck_data_export_job_quota_charged CHECK (quota_charged IN (0, 1));
ALTER TABLE device ADD CONSTRAINT ck_device_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE device_expense ADD CONSTRAINT ck_device_expense_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE event_record ADD CONSTRAINT ck_event_record_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE exercise_record ADD CONSTRAINT ck_exercise_record_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE expense ADD CONSTRAINT ck_expense_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE feedback ADD CONSTRAINT ck_feedback_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE feedback_comment ADD CONSTRAINT ck_feedback_comment_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE goal ADD CONSTRAINT ck_goal_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE goal ADD CONSTRAINT ck_goal_is_pinned CHECK (is_pinned IN (0, 1));
ALTER TABLE honor_category ADD CONSTRAINT ck_honor_category_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE honor_record ADD CONSTRAINT ck_honor_record_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE honor_record ADD CONSTRAINT ck_honor_record_is_public CHECK (is_public IN (0, 1));
ALTER TABLE honor_record ADD CONSTRAINT ck_honor_record_is_top CHECK (is_top IN (0, 1));
ALTER TABLE income ADD CONSTRAINT ck_income_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE life_footprint ADD CONSTRAINT ck_life_footprint_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE mail_log ADD CONSTRAINT ck_mail_log_status CHECK (status IN (0, 1));
ALTER TABLE mbti_result ADD CONSTRAINT ck_mbti_result_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE membership_provider ADD CONSTRAINT ck_membership_provider_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE membership_provider ADD CONSTRAINT ck_membership_provider_is_enabled CHECK (is_enabled IN (0, 1));
ALTER TABLE membership_record ADD CONSTRAINT ck_membership_record_auto_renew CHECK (auto_renew IN (0, 1));
ALTER TABLE membership_record ADD CONSTRAINT ck_membership_record_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE memo ADD CONSTRAINT ck_memo_hidden_content CHECK (hidden_content IN (0, 1));
ALTER TABLE memo ADD CONSTRAINT ck_memo_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE message ADD CONSTRAINT ck_message_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE message ADD CONSTRAINT ck_message_is_read CHECK (is_read IN (0, 1));
ALTER TABLE message ADD CONSTRAINT ck_message_type CHECK (type IN (0, 1));
ALTER TABLE milestone ADD CONSTRAINT ck_milestone_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE movie ADD CONSTRAINT ck_movie_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE notification_channel_config ADD CONSTRAINT ck_notification_channel_config_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE notification_channel_config ADD CONSTRAINT ck_notification_channel_config_is_enabled CHECK (is_enabled IN (0, 1));
ALTER TABLE notification_delivery ADD CONSTRAINT ck_notification_delivery_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE notification_preference ADD CONSTRAINT ck_notification_preference_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE notification_preference ADD CONSTRAINT ck_notification_preference_is_enabled CHECK (is_enabled IN (0, 1));
ALTER TABLE password_vault ADD CONSTRAINT ck_password_vault_favorite CHECK (favorite IN (0, 1));
ALTER TABLE password_vault ADD CONSTRAINT ck_password_vault_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE read_record ADD CONSTRAINT ck_read_record_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE sys_activity_log ADD CONSTRAINT ck_sys_activity_log_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE sys_activity_log ADD CONSTRAINT ck_sys_activity_log_success CHECK (success IN (0, 1));
ALTER TABLE sys_config ADD CONSTRAINT ck_sys_config_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE sys_dict_data ADD CONSTRAINT ck_sys_dict_data_is_default CHECK (is_default IN (0, 1));
ALTER TABLE sys_dict_data ADD CONSTRAINT ck_sys_dict_data_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE sys_dict_type ADD CONSTRAINT ck_sys_dict_type_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE sys_menu ADD CONSTRAINT ck_sys_menu_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE sys_region ADD CONSTRAINT ck_sys_region_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE sys_scheduled_job ADD CONSTRAINT ck_sys_scheduled_job_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE sys_scheduled_job ADD CONSTRAINT ck_sys_scheduled_job_is_enabled CHECK (is_enabled IN (0, 1));
ALTER TABLE sys_scheduled_job_log ADD CONSTRAINT ck_sys_scheduled_job_log_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE sys_user ADD CONSTRAINT ck_sys_user_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE task ADD CONSTRAINT ck_task_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE task_column ADD CONSTRAINT ck_task_column_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE task_detail ADD CONSTRAINT ck_task_detail_is_completed CHECK (is_completed IN (0, 1));
ALTER TABLE task_detail ADD CONSTRAINT ck_task_detail_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE task_detail ADD CONSTRAINT ck_task_detail_is_starred CHECK (is_starred IN (0, 1));
ALTER TABLE thought ADD CONSTRAINT ck_thought_hidden_content CHECK (hidden_content IN (0, 1));
ALTER TABLE thought ADD CONSTRAINT ck_thought_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE thought ADD CONSTRAINT ck_thought_is_pinned CHECK (is_pinned IN (0, 1));
ALTER TABLE thought_rela_event ADD CONSTRAINT ck_thought_rela_event_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE time_record ADD CONSTRAINT ck_time_record_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE time_record ADD CONSTRAINT ck_time_record_is_manual CHECK (is_manual IN (0, 1));
ALTER TABLE time_tracker_category ADD CONSTRAINT ck_time_tracker_category_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE time_tracker_category ADD CONSTRAINT ck_time_tracker_category_is_enabled CHECK (is_enabled IN (0, 1));
ALTER TABLE time_tracker_category ADD CONSTRAINT ck_time_tracker_category_is_track_time CHECK (is_track_time IN (0, 1));
ALTER TABLE user_bind ADD CONSTRAINT ck_user_bind_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE user_body_profile ADD CONSTRAINT ck_user_body_profile_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE user_dict_data ADD CONSTRAINT ck_user_dict_data_is_default CHECK (is_default IN (0, 1));
ALTER TABLE user_dict_data ADD CONSTRAINT ck_user_dict_data_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE user_dict_data ADD CONSTRAINT ck_user_dict_data_is_readonly CHECK (is_readonly IN (0, 1));
ALTER TABLE user_home_card_preference ADD CONSTRAINT ck_user_home_card_preference_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE user_home_card_preference ADD CONSTRAINT ck_user_home_card_preference_is_enabled CHECK (is_enabled IN (0, 1));
ALTER TABLE user_menu_hidden ADD CONSTRAINT ck_user_menu_hidden_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE user_quick_nav ADD CONSTRAINT ck_user_quick_nav_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE user_quick_nav ADD CONSTRAINT ck_user_quick_nav_is_enabled CHECK (is_enabled IN (0, 1));
ALTER TABLE user_secondary_lock_menu ADD CONSTRAINT ck_user_secondary_lock_menu_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE wardrobe_category ADD CONSTRAINT ck_wardrobe_category_is_deleted CHECK (is_deleted IN (0, 1));
ALTER TABLE wardrobe_item ADD CONSTRAINT ck_wardrobe_item_is_deleted CHECK (is_deleted IN (0, 1));
