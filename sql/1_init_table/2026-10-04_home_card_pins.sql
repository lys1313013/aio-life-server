-- 首页目标、纪念日固定字段；仅用于尚未执行本迁移的旧库，不可重复执行。
-- 阅读、观影按业务状态展示，会员全部展示，不新增首页固定字段。
ALTER TABLE `goal`
    ADD COLUMN `is_pinned` TINYINT NOT NULL DEFAULT 0 COMMENT '是否固定到首页：0-否，1-是' AFTER `tags`,
    ADD COLUMN `pinned_sort` INT NOT NULL DEFAULT 0 COMMENT '首页固定排序，数值越小越靠前' AFTER `is_pinned`,
    ADD INDEX `idx_home_pinned` (`user_id`, `is_deleted`, `is_pinned`, `pinned_sort`, `id`);

ALTER TABLE `anniversary_record`
    ADD COLUMN `is_pinned` TINYINT NOT NULL DEFAULT 0 COMMENT '是否固定到首页：0-否，1-是' AFTER `icon`,
    ADD COLUMN `pinned_sort` INT NOT NULL DEFAULT 0 COMMENT '首页固定排序，数值越小越靠前' AFTER `is_pinned`,
    ADD INDEX `idx_home_pinned` (`user_id`, `is_deleted`, `is_pinned`, `pinned_sort`, `id`);
