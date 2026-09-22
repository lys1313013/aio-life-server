USE `aio_life`;

CREATE TABLE IF NOT EXISTS `sys_activity_log` (
  `id` bigint NOT NULL,
  `log_type` varchar(16) NOT NULL COMMENT 'OPERATION 操作 / ACCESS 访问',
  `user_id` bigint DEFAULT NULL,
  `username` varchar(100) NOT NULL DEFAULT '',
  `nickname` varchar(100) NOT NULL DEFAULT '',
  `ip_address` varchar(64) NOT NULL DEFAULT '',
  `browser` varchar(100) NOT NULL DEFAULT '',
  `function_name` varchar(200) DEFAULT NULL,
  `function_item` varchar(100) DEFAULT NULL,
  `access_type` varchar(32) DEFAULT NULL,
  `request_path` varchar(255) DEFAULT NULL COMMENT '接口路由模板，不含查询参数',
  `success` tinyint(1) NOT NULL DEFAULT 1,
  `create_user` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_user` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_activity_type_time` (`log_type`, `is_deleted`, `create_time`, `id`),
  KEY `idx_activity_type_id` (`log_type`, `is_deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作和访问日志';

-- 新版服务启动前执行。历史数据没有浏览器、登出事件，不推测补造。
-- 旧自增 ID 按原值导入，重复执行按 ID 去重。新版服务启动后不要再次回填旧表。
INSERT INTO `sys_activity_log`
  (`id`, `log_type`, `user_id`, `username`, `nickname`, `ip_address`, `browser`,
   `access_type`, `request_path`, `success`, `create_time`, `update_time`, `is_deleted`)
SELECT l.id, 'ACCESS', l.user_id, l.username, COALESCE(u.nickname, ''), l.ip_address, '未知',
       '账密登录', '/auth/login', IF(l.user_id IS NULL, 0, 1), l.created_at, l.created_at, 0
FROM login_log l LEFT JOIN `user` u ON u.id = l.user_id
WHERE NOT EXISTS (SELECT 1 FROM sys_activity_log a WHERE a.id = l.id);
