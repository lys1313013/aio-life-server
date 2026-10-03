-- 旧库仅执行一次；先备份，应用发布前执行。原 cover 来源保持不变。
CREATE TABLE IF NOT EXISTS `storage_object` (
    `id` bigint NOT NULL,
    `bucket` varchar(63) NOT NULL,
    `object_key` varchar(255) NOT NULL,
    `sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `file_size` bigint NOT NULL,
    `content_type` varchar(100) NOT NULL,
    `create_user` bigint DEFAULT NULL,
    `create_time` datetime NOT NULL,
    `update_user` bigint DEFAULT NULL,
    `update_time` datetime NOT NULL,
    `is_deleted` tinyint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_storage_hash` (`bucket`, `sha256`),
    UNIQUE KEY `uk_storage_key` (`bucket`, `object_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不可变图片对象';

CREATE TABLE IF NOT EXISTS `image_import_task` (
    `id` bigint NOT NULL,
    `video_id` bigint NOT NULL,
    `cover_version` bigint NOT NULL,
    `source_url` varchar(1000) NOT NULL,
    `state` varchar(16) NOT NULL,
    `attempts` int NOT NULL DEFAULT 0,
    `next_attempt_at` datetime NOT NULL,
    `lease_token` varchar(32) DEFAULT NULL,
    `lease_until` datetime DEFAULT NULL,
    `error_code` varchar(64) DEFAULT NULL,
    `create_user` bigint DEFAULT NULL,
    `create_time` datetime NOT NULL,
    `update_user` bigint DEFAULT NULL,
    `update_time` datetime NOT NULL,
    `is_deleted` tinyint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_image_import_version` (`video_id`, `cover_version`),
    KEY `idx_image_import_due` (`state`, `next_attempt_at`, `lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频封面持久化导入任务';

ALTER TABLE `file` ADD COLUMN `storage_object_id` bigint DEFAULT NULL,
    ADD KEY `idx_file_storage_object` (`storage_object_id`);
ALTER TABLE `b_video` ADD COLUMN `cover_file_id` varchar(32) DEFAULT NULL,
    ADD COLUMN `cover_state` varchar(16) NOT NULL DEFAULT 'NONE',
    ADD COLUMN `cover_version` bigint NOT NULL DEFAULT 0;
