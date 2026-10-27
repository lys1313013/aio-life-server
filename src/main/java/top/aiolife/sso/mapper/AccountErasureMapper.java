package top.aiolife.sso.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;
import top.aiolife.sso.pojo.entity.UserEntity;

/** 注销只物理清理明确归属的个人记录；系统预设与共享资源保留。 */
@Mapper
public interface AccountErasureMapper {
    @Update("UPDATE user SET is_deleted=1, avatar_file_id=NULL, erasure_requested_at=#{now}, erasure_retry_at=#{retryAt} WHERE id=#{userId} AND is_deleted=0")
    int request(@Param("userId") long userId, @Param("now") LocalDateTime now, @Param("retryAt") LocalDateTime retryAt);

    @Update("UPDATE data_export_job SET cancel_requested=1 WHERE user_id=#{userId}")
    int cancelExports(@Param("userId") long userId);

    @Update("UPDATE notification_delivery SET status='FAILED', payload_ciphertext='' WHERE user_id=#{userId} AND status IN ('PENDING','RETRY')")
    int cancelNotifications(@Param("userId") long userId);

    @Select("SELECT id FROM user WHERE is_deleted=1 AND erasure_requested_at IS NOT NULL AND erasure_retry_at <= #{now} ORDER BY erasure_retry_at LIMIT 10")
    List<Long> pending(@Param("now") LocalDateTime now);

    @Select("SELECT * FROM user WHERE id=#{userId} AND is_deleted=1 AND erasure_requested_at IS NOT NULL FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    UserEntity lockPending(@Param("userId") long userId);

    @Update("UPDATE user SET erasure_retry_at=#{retryAt} WHERE id=#{userId} AND erasure_requested_at IS NOT NULL")
    int retry(@Param("userId") long userId, @Param("retryAt") LocalDateTime retryAt);

    @Select("SELECT COUNT(*) FROM data_export_job WHERE user_id=#{userId} AND status='RUNNING' AND lease_until > #{now}")
    long runningExports(@Param("userId") long userId, @Param("now") LocalDateTime now);

    @Select("SELECT object_key FROM data_export_job WHERE user_id=#{userId} AND object_key IS NOT NULL")
    List<String> exportObjects(@Param("userId") long userId);

    @Select("SELECT DISTINCT f.file_name FROM file f WHERE f.create_user=#{userId} AND f.storage_object_id IS NULL AND (f.biz_type IS NULL OR f.biz_type <> 'bank_card_template_cover') AND NOT EXISTS (SELECT 1 FROM file other WHERE other.file_name=f.file_name AND (other.create_user IS NULL OR other.create_user <> #{userId} OR other.biz_type='bank_card_template_cover'))")
    List<String> privateObjects(@Param("userId") long userId);

    @Select("SELECT DISTINCT o.id, o.bucket, o.object_key AS objectKey FROM storage_object o JOIN file f ON f.storage_object_id=o.id WHERE f.create_user=#{userId} AND NOT EXISTS (SELECT 1 FROM file other WHERE other.storage_object_id=o.id AND (other.create_user IS NULL OR other.create_user <> #{userId} OR other.biz_type='bank_card_template_cover'))")
    List<StoredObject> storedObjects(@Param("userId") long userId);

    @Select("SELECT COUNT(*) FROM file f WHERE (f.storage_object_id=#{id} OR f.file_name=#{key}) AND (f.create_user IS NULL OR f.create_user <> #{userId} OR f.biz_type='bank_card_template_cover')")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    long otherReferences(@Param("userId") long userId, @Param("id") Long id, @Param("key") String key);

    @Delete("DELETE FROM storage_object WHERE id=#{id} AND NOT EXISTS (SELECT 1 FROM file WHERE storage_object_id=#{id})")
    int purgeStoredObject(@Param("id") long id);

    @Delete("DELETE FROM user WHERE id=#{userId} AND is_deleted=1 AND erasure_requested_at IS NOT NULL")
    int finish(@Param("userId") long userId);

    record StoredObject(Long id, String bucket, String objectKey) {}

    @Delete("DELETE FROM feedback_comment WHERE user_id = #{userId} OR feedback_id IN (SELECT id FROM feedback WHERE user_id = #{userId})")
    int purgeFeedbackReplies(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM thought_rela_event WHERE create_user = #{userId} OR thought_id IN (SELECT id FROM thought WHERE user_id = #{userId})")
    int purgeThoughtEvents(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM record_article_annotation WHERE create_user = #{userId} OR article_id IN (SELECT id FROM record_article WHERE create_user = #{userId})")
    int purgeAnnotations(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM record_article WHERE create_user = #{userId}")
    int purgeArticles(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM api_key_log WHERE api_key_id IN (SELECT id FROM api_key WHERE user_id = #{userId})")
    int purgeApiLogs(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM image_import_task WHERE video_id IN (SELECT id FROM b_video WHERE user_id = #{userId})")
    int purgeImageTasks(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM message WHERE receiver_id = #{userId} OR sender_id = #{userId}")
    int purgeMessages(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM login_log WHERE user_id = #{userId} OR (username = (SELECT username FROM user WHERE id = #{userId}) AND created_at <= (SELECT erasure_requested_at FROM user WHERE id = #{userId}))")
    int purgeLoginLogs(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM mail_log WHERE send_to = #{email} AND create_time <= (SELECT erasure_requested_at FROM user WHERE id = #{userId})")
    int purgeMailLogs(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM file WHERE create_user = #{userId} AND (biz_type IS NULL OR biz_type <> 'bank_card_template_cover')")
    int purgeFiles(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `user_bind` WHERE user_id = #{userId}")
    int purgeUserBind(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `password_vault` WHERE user_id = #{userId}")
    int purgePasswordVault(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `user_secondary_lock_menu` WHERE user_id = #{userId}")
    int purgeUserSecondaryLockMenu(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `user_menu_hidden` WHERE user_id = #{userId}")
    int purgeUserMenuHidden(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `user_quick_nav` WHERE user_id = #{userId}")
    int purgeUserQuickNav(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `api_key` WHERE user_id = #{userId}")
    int purgeApiKey(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `sys_activity_log` WHERE user_id = #{userId}")
    int purgeSysActivityLog(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `user_dict_data` WHERE user_id = #{userId}")
    int purgeUserDictData(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `user_dict_type` WHERE user_id = #{userId}")
    int purgeUserDictType(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `feedback` WHERE user_id = #{userId}")
    int purgeFeedback(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `time_tracker_category` WHERE user_id = #{userId}")
    int purgeTimeTrackerCategory(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `time_record` WHERE user_id = #{userId}")
    int purgeTimeRecord(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `task` WHERE user_id = #{userId}")
    int purgeTask(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `task_column` WHERE user_id = #{userId}")
    int purgeTaskColumn(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `task_detail` WHERE user_id = #{userId}")
    int purgeTaskDetail(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `goal` WHERE user_id = #{userId}")
    int purgeGoal(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `honor_category` WHERE user_id = #{userId}")
    int purgeHonorCategory(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `honor_record` WHERE user_id = #{userId}")
    int purgeHonorRecord(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `anniversary_record` WHERE user_id = #{userId}")
    int purgeAnniversaryRecord(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `milestone` WHERE user_id = #{userId}")
    int purgeMilestone(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `memo` WHERE user_id = #{userId}")
    int purgeMemo(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `thought` WHERE user_id = #{userId}")
    int purgeThought(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `expense` WHERE user_id = #{userId}")
    int purgeExpense(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `bank_card` WHERE user_id = #{userId}")
    int purgeBankCard(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `bank_card_tag_rel` WHERE user_id = #{userId}")
    int purgeBankCardTagRel(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `income` WHERE user_id = #{userId}")
    int purgeIncome(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `exercise_record` WHERE user_id = #{userId}")
    int purgeExerciseRecord(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `read_record` WHERE user_id = #{userId}")
    int purgeReadRecord(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `movie` WHERE user_id = #{userId}")
    int purgeMovie(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `b_video` WHERE user_id = #{userId}")
    int purgeBVideo(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `performance` WHERE user_id = #{userId}")
    int purgePerformance(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `device` WHERE user_id = #{userId}")
    int purgeDevice(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `membership_record` WHERE user_id = #{userId}")
    int purgeMembershipRecord(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `notification_channel_config` WHERE user_id = #{userId}")
    int purgeNotificationChannelConfig(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `notification_preference` WHERE user_id = #{userId}")
    int purgeNotificationPreference(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `notification_delivery` WHERE user_id = #{userId}")
    int purgeNotificationDelivery(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `wardrobe_category` WHERE user_id = #{userId}")
    int purgeWardrobeCategory(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `wardrobe_item` WHERE user_id = #{userId}")
    int purgeWardrobeItem(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `mbti_result` WHERE user_id = #{userId}")
    int purgeMbtiResult(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `cbti_result` WHERE user_id = #{userId}")
    int purgeCbtiResult(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `user_home_card_preference` WHERE user_id = #{userId}")
    int purgeUserHomeCardPreference(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `user_body_profile` WHERE user_id = #{userId}")
    int purgeUserBodyProfile(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `life_footprint` WHERE user_id = #{userId}")
    int purgeLifeFootprint(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `device_expense` WHERE user_id = #{userId}")
    int purgeDeviceExpense(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `data_export_audit_event` WHERE user_id = #{userId}")
    int purgeDataExportAuditEvent(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `data_export_grant` WHERE user_id = #{userId}")
    int purgeDataExportGrant(@Param("userId") long userId, @Param("email") String email);

    @Delete("DELETE FROM `data_export_job` WHERE user_id = #{userId}")
    int purgeDataExportJob(@Param("userId") long userId, @Param("email") String email);

    default void purgePersonalRecords(long userId, String email) {
        purgeFeedbackReplies(userId, email);
        purgeThoughtEvents(userId, email);
        purgeAnnotations(userId, email);
        purgeArticles(userId, email);
        purgeApiLogs(userId, email);
        purgeImageTasks(userId, email);
        purgeMessages(userId, email);
        purgeLoginLogs(userId, email);
        purgeMailLogs(userId, email);
        purgeFiles(userId, email);
        purgeUserBind(userId, email);
        purgePasswordVault(userId, email);
        purgeUserSecondaryLockMenu(userId, email);
        purgeUserMenuHidden(userId, email);
        purgeUserQuickNav(userId, email);
        purgeApiKey(userId, email);
        purgeSysActivityLog(userId, email);
        purgeUserDictData(userId, email);
        purgeUserDictType(userId, email);
        purgeFeedback(userId, email);
        purgeTimeTrackerCategory(userId, email);
        purgeTimeRecord(userId, email);
        purgeTask(userId, email);
        purgeTaskColumn(userId, email);
        purgeTaskDetail(userId, email);
        purgeGoal(userId, email);
        purgeHonorCategory(userId, email);
        purgeHonorRecord(userId, email);
        purgeAnniversaryRecord(userId, email);
        purgeMilestone(userId, email);
        purgeMemo(userId, email);
        purgeThought(userId, email);
        purgeExpense(userId, email);
        purgeBankCard(userId, email);
        purgeBankCardTagRel(userId, email);
        purgeIncome(userId, email);
        purgeExerciseRecord(userId, email);
        purgeReadRecord(userId, email);
        purgeMovie(userId, email);
        purgeBVideo(userId, email);
        purgePerformance(userId, email);
        purgeDevice(userId, email);
        purgeMembershipRecord(userId, email);
        purgeNotificationChannelConfig(userId, email);
        purgeNotificationPreference(userId, email);
        purgeNotificationDelivery(userId, email);
        purgeWardrobeCategory(userId, email);
        purgeWardrobeItem(userId, email);
        purgeMbtiResult(userId, email);
        purgeCbtiResult(userId, email);
        purgeUserHomeCardPreference(userId, email);
        purgeUserBodyProfile(userId, email);
        purgeLifeFootprint(userId, email);
        purgeDeviceExpense(userId, email);
        purgeDataExportAuditEvent(userId, email);
        purgeDataExportGrant(userId, email);
        purgeDataExportJob(userId, email);
    }
}
