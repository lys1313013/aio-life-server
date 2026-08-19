-- 统一审计字段：
-- create_user/update_user BIGINT NULL，create_time/update_time DATETIME。
-- 本脚本用于从 2026-08-18 全量结构升级已有数据库。

USE `aio-life`;

-- 用户表：保留原时间数据，同时补齐可空的操作人字段。
ALTER TABLE `user`
    CHANGE COLUMN `created_at` `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    CHANGE COLUMN `updated_at` `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `user`
    ADD COLUMN `create_user` bigint(20) DEFAULT NULL COMMENT '创建人' AFTER `nickname`,
    ADD COLUMN `update_user` bigint(20) DEFAULT NULL COMMENT '更新人' AFTER `create_time`;

-- 字典表旧字段是 varchar。只迁移可无损转换的数字用户 ID；空串或历史用户名按新规范置空。
ALTER TABLE `sys_dict_type`
    ADD COLUMN `create_user` bigint(20) DEFAULT NULL COMMENT '创建人' AFTER `status`,
    ADD COLUMN `update_user` bigint(20) DEFAULT NULL COMMENT '更新人' AFTER `create_time`;

UPDATE `sys_dict_type`
SET `create_user` = CASE
        WHEN TRIM(`create_by`) REGEXP '^[0-9]+$' THEN CAST(TRIM(`create_by`) AS UNSIGNED)
        ELSE NULL
    END,
    `update_user` = CASE
        WHEN TRIM(`update_by`) REGEXP '^[0-9]+$' THEN CAST(TRIM(`update_by`) AS UNSIGNED)
        ELSE NULL
    END;

ALTER TABLE `sys_dict_type`
    DROP COLUMN `create_by`,
    DROP COLUMN `update_by`;

ALTER TABLE `sys_dict_data`
    ADD COLUMN `create_user` bigint(20) DEFAULT NULL COMMENT '创建人' AFTER `status`,
    ADD COLUMN `update_user` bigint(20) DEFAULT NULL COMMENT '更新人' AFTER `create_time`;

UPDATE `sys_dict_data`
SET `create_user` = CASE
        WHEN TRIM(`create_by`) REGEXP '^[0-9]+$' THEN CAST(TRIM(`create_by`) AS UNSIGNED)
        ELSE NULL
    END,
    `update_user` = CASE
        WHEN TRIM(`update_by`) REGEXP '^[0-9]+$' THEN CAST(TRIM(`update_by`) AS UNSIGNED)
        ELSE NULL
    END;

ALTER TABLE `sys_dict_data`
    DROP COLUMN `create_by`,
    DROP COLUMN `update_by`;

-- 原结构中操作人字段为 NOT NULL 的表统一放宽，以支持系统任务和初始化数据。
ALTER TABLE `user_bind`
    MODIFY COLUMN `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    MODIFY COLUMN `update_user` bigint(20) DEFAULT NULL COMMENT '更新人';

ALTER TABLE `api_key`
    MODIFY COLUMN `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    MODIFY COLUMN `update_user` bigint(20) DEFAULT NULL COMMENT '更新人';

ALTER TABLE `time_tracker_category`
    MODIFY COLUMN `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    MODIFY COLUMN `update_user` bigint(20) DEFAULT NULL COMMENT '更新人';

ALTER TABLE `time_record`
    MODIFY COLUMN `create_user` bigint(20) DEFAULT NULL COMMENT '用户ID',
    MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `task_detail`
    MODIFY COLUMN `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    MODIFY COLUMN `update_user` bigint(20) DEFAULT NULL COMMENT '更新人';

ALTER TABLE `milestone`
    MODIFY COLUMN `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    MODIFY COLUMN `update_user` bigint(20) DEFAULT NULL COMMENT '更新人';

ALTER TABLE `expense`
    MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `income`
    MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `exercise_record`
    MODIFY COLUMN `create_user` bigint(20) DEFAULT NULL COMMENT '创建用户';

ALTER TABLE `b_video`
    MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `message`
    MODIFY COLUMN `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    MODIFY COLUMN `update_user` bigint(20) DEFAULT NULL COMMENT '更新人';

ALTER TABLE `notification_channel_config`
    MODIFY COLUMN `create_user` bigint DEFAULT NULL,
    MODIFY COLUMN `update_user` bigint DEFAULT NULL;

ALTER TABLE `notification_preference`
    MODIFY COLUMN `create_user` bigint DEFAULT NULL,
    MODIFY COLUMN `update_user` bigint DEFAULT NULL;

ALTER TABLE `notification_delivery`
    MODIFY COLUMN `create_user` bigint DEFAULT NULL,
    MODIFY COLUMN `update_user` bigint DEFAULT NULL;

ALTER TABLE `chat_message`
    MODIFY COLUMN `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    MODIFY COLUMN `update_user` bigint(20) DEFAULT NULL COMMENT '更新人';

ALTER TABLE `file`
    MODIFY COLUMN `create_user` bigint DEFAULT NULL COMMENT '创建人',
    MODIFY COLUMN `update_user` bigint DEFAULT NULL COMMENT '更新人';
