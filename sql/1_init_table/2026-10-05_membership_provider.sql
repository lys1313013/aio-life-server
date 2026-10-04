USE `aio_life`;

CREATE TABLE IF NOT EXISTS `membership_provider` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `name` varchar(100) NOT NULL COMMENT '平台名称',
    `code` varchar(50) NOT NULL COMMENT '稳定编码',
    `category` varchar(50) NOT NULL DEFAULT 'other' COMMENT '默认分类:video/music/shopping/cloud/study/game/other',
    `icon_key` varchar(64) DEFAULT NULL COMMENT '内置图标标识，不支持上传',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '展示顺序',
    `is_enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用:1-启用 0-停用',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除',
    `active_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `is_deleted` = 0 THEN 1 ELSE NULL END) STORED COMMENT '仅有效平台参与编码唯一约束',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_membership_provider_code` (`code`, `active_flag`),
    KEY `idx_membership_provider_enabled_sort` (`is_enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员平台表';

ALTER TABLE `membership_record`
    ADD COLUMN `provider_id` bigint DEFAULT NULL COMMENT '会员平台ID，旧记录或自定义平台可为空' AFTER `category`,
    ADD KEY `idx_membership_provider_id` (`provider_id`);
