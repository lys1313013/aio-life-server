-- 目的：允许系统 CBTI 人格图片写入附件表，并约束同一人格只有一张有效图片。
-- 前置：PostgreSQL 18+；已执行 V20261118_07；执行前备份并暂停附件写入。
-- 影响：只调整附件归属约束、单图部分唯一索引及注释，不修改已有记录。
-- 重跑：Flyway 成功后不重跑；锁等待超时、CBTI 图片带用户归属或有效图片重复时整次回滚，核对后重试。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate。
-- 验证：无用户归属的 CBTI 图片可入库；同一人格的第二张有效图片会被拒绝，软删除旧图后可换图。

SET LOCAL lock_timeout = '10s';

ALTER TABLE attachment DROP CONSTRAINT ck_attachment_owner;
ALTER TABLE attachment ADD CONSTRAINT ck_attachment_owner CHECK (
  (user_id IS NOT NULL OR biz_type IN ('bank_card_template_face', 'cbti_personality_image'))
  AND (biz_type <> 'cbti_personality_image' OR user_id IS NULL)
);
COMMENT ON CONSTRAINT ck_attachment_owner ON attachment IS 'CBTI 人格图片归系统所有；只有人格图片和公共卡面模板允许没有用户归属';

DROP INDEX uk_attachment_single_slot;
CREATE UNIQUE INDEX uk_attachment_single_slot ON attachment (biz_type, biz_id)
  WHERE is_deleted = 0 AND biz_id IS NOT NULL AND biz_type IN (
    'movie_cover', 'read_cover', 'device_cover', 'wardrobe_item_cover', 'b_video_cover',
    'avatar', 'bank_card_face', 'bank_card_template_face', 'cbti_personality_image'
  );
COMMENT ON INDEX uk_attachment_single_slot IS '含 CBTI 人格图片在内的单值业务位置只允许一条有效附件';

COMMENT ON COLUMN attachment.user_id IS '归属用户ID；CBTI 人格图片为空，公共卡面模板可为空';
COMMENT ON COLUMN attachment.biz_type IS '附件用途：movie_cover/read_cover/device_cover/wardrobe_item_cover/b_video_cover/avatar/bank_card_face/bank_card_template_face/cbti_personality_image/feedback/feedback_comment/honor_record/event_record';
COMMENT ON COLUMN attachment.is_public IS '0私有，1公开；服务端按当前头像、已发布公共卡面或 CBTI 人格图片设置';
