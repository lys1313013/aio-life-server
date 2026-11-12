-- 目的：把已有库的 7 个 CHECK 约束表达式对齐到当前基线写法，语义不变。
-- 前置：PostgreSQL 18+，已执行当前 12 文件链或等价的旧增量链。
-- 影响：仅重建 bank_card_cover_template、goal、user_body_profile 的 CHECK 约束，不改数据。
-- 重跑：由 Flyway 版本历史保证只执行一次；失败事务回滚后修正原因再迁移。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；失败事务回滚。

SET LOCAL lock_timeout = '10s';

ALTER TABLE "bank_card_cover_template" DROP CONSTRAINT "chk_cover_template_type";
ALTER TABLE "bank_card_cover_template" ADD CONSTRAINT "chk_cover_template_type" CHECK (("card_type" in ('debit','credit')));
ALTER TABLE "goal" DROP CONSTRAINT "ck_goal_progress_mode";
ALTER TABLE "goal" ADD CONSTRAINT "ck_goal_progress_mode" CHECK (("progress_mode" in ('manual','graphql')));
ALTER TABLE "goal" DROP CONSTRAINT "ck_goal_target_operator";
ALTER TABLE "goal" ADD CONSTRAINT "ck_goal_target_operator" CHECK (("target_operator" in ('gte','gt')));
ALTER TABLE "user_body_profile" DROP CONSTRAINT "ck_body_profile_arm_state";
ALTER TABLE "user_body_profile" ADD CONSTRAINT "ck_body_profile_arm_state" CHECK (("arm_state" in ('relaxed','flexed')));
ALTER TABLE "user_body_profile" DROP CONSTRAINT "ck_body_profile_gender";
ALTER TABLE "user_body_profile" ADD CONSTRAINT "ck_body_profile_gender" CHECK (("gender" in ('male','female')));
ALTER TABLE "user_body_profile" DROP CONSTRAINT "ck_body_profile_shoe_system";
ALTER TABLE "user_body_profile" ADD CONSTRAINT "ck_body_profile_shoe_system" CHECK (("shoe_system" in ('EU','CN','US','UK')));
ALTER TABLE "user_body_profile" DROP CONSTRAINT "ck_body_profile_side";
ALTER TABLE "user_body_profile" ADD CONSTRAINT "ck_body_profile_side" CHECK (("measurement_side" in ('left','right')));
