-- 补齐业务表的统一审计字段和逻辑删除字段。
-- 日志表（api_key_log、login_log、mail_log）及枚举定义表（enum_type）不适用本规范。

USE `aio-life`;

ALTER TABLE `sys_dict_type`
    ADD COLUMN `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除: 0-未删除, 1-已删除' AFTER `remark`;

ALTER TABLE `sys_dict_data`
    ADD COLUMN `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除: 0-未删除, 1-已删除' AFTER `remark`;

ALTER TABLE `time_record`
    ADD COLUMN `update_user` bigint(20) DEFAULT NULL COMMENT '更新人' AFTER `create_time`;

ALTER TABLE `task`
    ADD COLUMN `create_user` bigint(20) DEFAULT NULL COMMENT '创建人' AFTER `is_deleted`,
    ADD COLUMN `update_user` bigint(20) DEFAULT NULL COMMENT '更新人' AFTER `create_time`;

ALTER TABLE `task_column`
    ADD COLUMN `create_user` bigint(20) DEFAULT NULL COMMENT '创建人' AFTER `is_deleted`,
    ADD COLUMN `update_user` bigint(20) DEFAULT NULL COMMENT '更新人' AFTER `create_time`;

ALTER TABLE `honor_category`
    ADD COLUMN `create_user` bigint DEFAULT NULL COMMENT '创建人ID' AFTER `is_deleted`,
    ADD COLUMN `update_user` bigint DEFAULT NULL COMMENT '更新人ID' AFTER `create_time`;

ALTER TABLE `device`
    ADD COLUMN `create_user` bigint(20) DEFAULT NULL COMMENT '创建人' AFTER `file_id`,
    ADD COLUMN `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间' AFTER `create_user`,
    ADD COLUMN `update_user` bigint(20) DEFAULT NULL COMMENT '更新人' AFTER `create_time`,
    ADD COLUMN `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `update_user`,
    ADD COLUMN `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除: 0-未删除, 1-已删除' AFTER `update_time`;

ALTER TABLE `llm_key`
    ADD COLUMN `create_user` bigint(20) DEFAULT NULL COMMENT '创建人' AFTER `is_default`,
    ADD COLUMN `update_user` bigint(20) DEFAULT NULL COMMENT '更新人' AFTER `create_time`,
    ADD COLUMN `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除: 0-未删除, 1-已删除' AFTER `update_time`;
