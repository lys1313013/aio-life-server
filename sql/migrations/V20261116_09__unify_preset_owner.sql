-- 目的：统一公共预设的表示方式：user_id IS NULL 表示系统预设，禁止 0 号用户；衣柜分类删除冗余的 category_type。
-- 前置：PostgreSQL 18+；已执行当前初始迁移及此前全部增量，time_tracker_category、user_dict_data、wardrobe_category 为当前结构。
-- 影响：把 time_tracker_category、user_dict_data 中 user_id = 0 的预设行改为 NULL，创建人/更新人 0 同步改为 NULL；两列取消 NOT NULL；新增归属检查与预设检索索引；删除 wardrobe_category.category_type，其语义由 user_id 是否为空表达。保留全部行、覆盖关系、template_id、排序与启用状态。
-- 重跑：Flyway 成功后不重跑；失败事务整体回滚后修正原因再迁移，不手工补执行。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；遇错停止。

ALTER TABLE time_tracker_category ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE user_dict_data ALTER COLUMN user_id DROP NOT NULL;

-- 预设行不再借用 0 号用户；审计人同为系统操作，置空。
UPDATE time_tracker_category
SET user_id = NULL,
    create_user = CASE WHEN create_user = 0 THEN NULL ELSE create_user END,
    update_user = CASE WHEN update_user = 0 THEN NULL ELSE update_user END
WHERE user_id = 0;

UPDATE user_dict_data
SET user_id = NULL,
    create_user = CASE WHEN create_user = 0 THEN NULL ELSE create_user END,
    update_user = CASE WHEN update_user = 0 THEN NULL ELSE update_user END
WHERE user_id = 0;

ALTER TABLE time_tracker_category ADD CONSTRAINT ck_time_tracker_category_owner CHECK (user_id IS NULL OR user_id > 0);
COMMENT ON CONSTRAINT ck_time_tracker_category_owner ON time_tracker_category IS '归属只允许为空（系统预设）或正数用户ID，不再使用0';
ALTER TABLE user_dict_data ADD CONSTRAINT ck_user_dict_data_owner CHECK (user_id IS NULL OR user_id > 0);
COMMENT ON CONSTRAINT ck_user_dict_data_owner ON user_dict_data IS '归属只允许为空（系统预设）或正数用户ID，不再使用0';
ALTER TABLE wardrobe_category ADD CONSTRAINT ck_wardrobe_category_owner CHECK (user_id IS NULL OR user_id > 0);
COMMENT ON CONSTRAINT ck_wardrobe_category_owner ON wardrobe_category IS '归属只允许为空（系统预设）或正数用户ID，不再使用0';
ALTER TABLE honor_category ADD CONSTRAINT ck_honor_category_owner CHECK (user_id IS NULL OR user_id > 0);
COMMENT ON CONSTRAINT ck_honor_category_owner ON honor_category IS '归属只允许为空（系统预设）或正数用户ID，不再使用0';

COMMENT ON COLUMN time_tracker_category.user_id IS '所属用户ID；NULL表示系统公共预设';
COMMENT ON COLUMN user_dict_data.user_id IS '所属用户ID；NULL表示系统公共预设';
COMMENT ON COLUMN wardrobe_category.user_id IS '所属用户ID；NULL表示系统公共预设';
COMMENT ON COLUMN honor_category.user_id IS '用户ID；NULL表示系统预设分类';

CREATE INDEX idx_time_tracker_category_preset ON time_tracker_category (parent_id, sort_order, id)
    WHERE user_id IS NULL AND is_deleted = 0;
COMMENT ON INDEX idx_time_tracker_category_preset IS '系统预设时迹分类按层级与排序检索';
CREATE INDEX idx_user_dict_data_preset ON user_dict_data (dict_type, sort_order, id)
    WHERE user_id IS NULL AND is_deleted = 0;
COMMENT ON INDEX idx_user_dict_data_preset IS '系统预设字典项按类型与排序检索';

-- 预设与用户分类统一由 user_id 是否为空区分；删除冗余的类型标记及其索引。
ALTER TABLE wardrobe_category DROP COLUMN category_type;
CREATE INDEX idx_wardrobe_category_preset_sort ON wardrobe_category (sort_order, id)
    WHERE user_id IS NULL AND is_deleted = 0;
COMMENT ON INDEX idx_wardrobe_category_preset_sort IS '系统预设衣柜分类按排序检索';
CREATE INDEX idx_wardrobe_category_user_sort ON wardrobe_category (user_id, sort_order, id)
    WHERE is_deleted = 0;
COMMENT ON INDEX idx_wardrobe_category_user_sort IS '用户自建衣柜分类按用户与排序检索';
