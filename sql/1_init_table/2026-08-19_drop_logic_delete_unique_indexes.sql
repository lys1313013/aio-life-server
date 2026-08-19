-- 逻辑删除表不再通过原始业务字段唯一索引约束历史数据。
-- 唯一性由业务层针对 is_deleted = 0 的有效记录校验。

ALTER TABLE `user` DROP INDEX `uk_username`;
ALTER TABLE `sys_dict_type` DROP INDEX `dict_type`;
ALTER TABLE `user_quick_nav` DROP INDEX `uk_user_menu`;
ALTER TABLE `system_config` DROP INDEX `uk_config_key`;
ALTER TABLE `api_key` DROP INDEX `uk_api_key`;
ALTER TABLE `notification_channel_config` DROP INDEX `uk_user_channel`;
ALTER TABLE `notification_preference` DROP INDEX `uk_user_biz_channel`;
ALTER TABLE `notification_delivery` DROP INDEX `uk_dedup_user_channel`;
