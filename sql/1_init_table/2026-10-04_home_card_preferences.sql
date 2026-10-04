-- Per-user presentation preferences. Reset uses physical DELETE; business records are untouched.
CREATE TABLE IF NOT EXISTS `user_home_card_preference` (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `card_key` varchar(64) NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT 1,
  `sort_order` int NOT NULL DEFAULT 0,
  `create_user` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_user` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `is_deleted` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_home_card` (`user_id`, `card_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户首页卡片偏好';
