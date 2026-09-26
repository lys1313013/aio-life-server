-- 时迹最多两级。先执行迁移，再部署后端；不修改 time_record.category_id。
ALTER TABLE `time_tracker_category`
    ADD COLUMN `parent_id` bigint DEFAULT NULL
        COMMENT '上级分类ID：0为一级；覆盖记录NULL表示继承公共分类' AFTER `template_id`,
    ADD INDEX `idx_category_user_parent` (`user_id`, `parent_id`),
    ADD INDEX `idx_category_user_template` (`user_id`, `template_id`);

UPDATE `time_tracker_category`
SET `parent_id` = 0
WHERE `template_id` IS NULL AND `parent_id` IS NULL;
