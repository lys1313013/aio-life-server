-- 目的：新增个人数据导出任务、月额度账本、审计和一次性授权。
-- 前置：MySQL 5.7.44+；已存在用户和业务表。
-- 影响：只新增4张表，不回填或修改业务数据；月度最多5次，无每日限制。
-- 重跑：仅补缺失表和控制行；已有表升级必须追加ALTER，不以IF NOT EXISTS代替。
-- 执行：按upgrade-plan.json顺序执行，遇错停止。
-- 恢复：DDL不能整体回滚；先关闭导出开关，保留已有任务和审计，核对结构后重跑。

CREATE TABLE IF NOT EXISTS `data_export_audit_event` (
  `id` BIGINT NOT NULL COMMENT '记录ID，由应用生成雪花ID',
  `job_id` BIGINT DEFAULT NULL COMMENT '关联任务ID，建单前或无有效任务时为空',
  `user_id` BIGINT DEFAULT NULL COMMENT '所属用户ID，用于个人数据隔离',
  `event_type` VARCHAR(128) NOT NULL COMMENT '审计事件类型',
  `result_code` VARCHAR(128) NOT NULL COMMENT '安全结果码，不含业务内容',
  `request_id` VARCHAR(64) NOT NULL COMMENT '服务端事件请求标识',
  `client_ip` VARCHAR(128) DEFAULT NULL COMMENT '可信代理处理后的来源IP，后台任务为空',
  `trace_id` VARCHAR(64) DEFAULT NULL COMMENT '链路追踪标识，无上下文为空',
  `details` MEDIUMTEXT DEFAULT NULL COMMENT '白名单事件元数据，不含正文或凭据',
  `event_time` DATETIME(3) NOT NULL COMMENT '审计事件时间，上海时区',
  `create_user` BIGINT DEFAULT NULL COMMENT '创建人用户ID，系统操作为空',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
  `update_user` BIGINT DEFAULT NULL COMMENT '最后更新人用户ID，系统操作为空',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除、1已删除',
  -- 主键保护记录唯一性。
  PRIMARY KEY (`id`),
  -- 按所属用户、状态或关联任务进行范围查询与清理。
  KEY `idx_data_export_audit_event_job` (`job_id`, `event_time`, `id`),
  -- 按所属用户、状态或关联任务进行范围查询与清理。
  KEY `idx_data_export_audit_event_user` (`user_id`, `event_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人数据导出追加审计事件';

CREATE TABLE IF NOT EXISTS `data_export_coordinator` (
  `id` BIGINT NOT NULL COMMENT '记录ID，由应用生成雪花ID，控制行固定为1',
  `name` VARCHAR(128) NOT NULL COMMENT '控制行名称，用于串行化领取和月额度结算',
  `create_user` BIGINT DEFAULT NULL COMMENT '创建人用户ID，系统操作为空',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
  `update_user` BIGINT DEFAULT NULL COMMENT '最后更新人用户ID，系统操作为空',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除、1已删除',
  -- 主键保护记录唯一性。
  PRIMARY KEY (`id`),
  -- 唯一键防止幂等申请或授权票据重复。
  UNIQUE KEY `uk_data_export_coordinator_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人数据导出全局队列控制';

CREATE TABLE IF NOT EXISTS `data_export_grant` (
  `id` BIGINT NOT NULL COMMENT '记录ID，由应用生成雪花ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID，用于个人数据隔离',
  `token_hash` VARCHAR(64) NOT NULL COMMENT '一次性核验票据SHA256摘要',
  `session_hash` VARCHAR(64) NOT NULL COMMENT '申请会话Token摘要，不保存原值',
  `security_hash` VARCHAR(64) NOT NULL COMMENT '账户凭据状态摘要，用于凭据变更后撤销授权',
  `scope_hash` VARCHAR(64) NOT NULL COMMENT '获核验范围和目录版本的SHA256摘要',
  `purpose` VARCHAR(128) NOT NULL COMMENT '授权用途：CREATE创建、DOWNLOAD下载',
  `job_id` BIGINT DEFAULT NULL COMMENT '关联任务ID，建单前或无有效任务时为空',
  `expires_at` DATETIME(3) NOT NULL COMMENT '一次性授权到期时间',
  `consumed` INT NOT NULL COMMENT '核验票据状态：0=未消费，1=已消费',
  `create_user` BIGINT DEFAULT NULL COMMENT '创建人用户ID，系统操作为空',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
  `update_user` BIGINT DEFAULT NULL COMMENT '最后更新人用户ID，系统操作为空',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除、1已删除',
  -- 主键保护记录唯一性。
  PRIMARY KEY (`id`),
  -- 唯一键防止幂等申请或授权票据重复。
  UNIQUE KEY `uk_data_export_grant_token` (`token_hash`),
  -- 按所属用户、状态或关联任务进行范围查询与清理。
  KEY `idx_data_export_grant_expiry` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人数据导出一次性身份核验授权';

CREATE TABLE IF NOT EXISTS `data_export_job` (
  `id` BIGINT NOT NULL COMMENT '记录ID，由应用生成雪花ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID，用于个人数据隔离',
  `request_key` VARCHAR(64) NOT NULL COMMENT '客户端幂等键，同一用户内唯一',
  `request_hash` VARCHAR(64) NOT NULL COMMENT '归一化申请范围的SHA256摘要',
  `scope_json` TEXT NOT NULL COMMENT '已授权数据集键数组，不含业务正文',
  `session_hash` VARCHAR(64) NOT NULL COMMENT '申请会话Token摘要，不保存原值',
  `security_hash` VARCHAR(64) NOT NULL COMMENT '账户凭据状态摘要，用于凭据变更后撤销授权',
  `quota_month` VARCHAR(7) NOT NULL COMMENT '申请所属自然月，上海时区yyyy-MM',
  `quota_charged` INT NOT NULL COMMENT '月额度标记：1=预占或已扣除，0=已退还',
  `status` VARCHAR(128) NOT NULL COMMENT '任务状态：QUEUED排队、RUNNING执行、SUCCEEDED成功、FAILED失败、CANCELLED取消、REVOKED撤销',
  `phase` VARCHAR(128) NOT NULL COMMENT '执行阶段：QUEUED排队、CAPTURING读取、DONE终态',
  `attempt` INT NOT NULL COMMENT '尝试次数，初始0，每次领取加1',
  `lease_token` VARCHAR(128) DEFAULT NULL COMMENT '当前执行租约随机标识，防止迟到执行者发布',
  `lease_until` DATETIME(3) DEFAULT NULL COMMENT '当前租约到期时间，未领取为空',
  `deadline` DATETIME(3) NOT NULL COMMENT '任务最迟结束时间，上海时区',
  `started_at` DATETIME(3) DEFAULT NULL COMMENT '首次开始读取时间，尚未执行为空',
  `finished_at` DATETIME(3) DEFAULT NULL COMMENT '终态时间，尚未结束为空',
  `cancel_requested` INT NOT NULL COMMENT '取消请求：0=未请求，1=用户已请求',
  `error_code` VARCHAR(128) DEFAULT NULL COMMENT '安全错误码，成功或尚未失败为空',
  `summary_json` MEDIUMTEXT DEFAULT NULL COMMENT '数据集行数及文件摘要清单，完成前为空',
  `object_key` VARCHAR(255) DEFAULT NULL COMMENT '专用私有桶内部对象键，不返回客户端',
  `file_name` VARCHAR(128) DEFAULT NULL COMMENT '用户下载文件名，不含个人信息',
  `content_type` VARCHAR(128) DEFAULT NULL COMMENT '生成文件MIME类型，完成前为空',
  `sha256` VARCHAR(64) DEFAULT NULL COMMENT '生成文件SHA256摘要，完成前为空',
  `file_bytes` BIGINT DEFAULT NULL COMMENT '文件字节数，完成前为空，0表示空文件',
  `row_count` INT NOT NULL COMMENT '业务数据总行数，0表示空数据',
  `artifact_status` VARCHAR(128) NOT NULL COMMENT '文件状态：NONE未生成、AVAILABLE可用、EXPIRED过期、DELETED删除、REVOKED撤销',
  `expires_at` DATETIME(3) DEFAULT NULL COMMENT '文件或授权到期时间，文件完成前为空',
  `download_count` INT NOT NULL COMMENT '已授权开始传输次数，中断也计次',
  `create_user` BIGINT DEFAULT NULL COMMENT '创建人用户ID，系统操作为空',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
  `update_user` BIGINT DEFAULT NULL COMMENT '最后更新人用户ID，系统操作为空',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除、1已删除，额度有效期内禁止删除任务',
  -- 主键保护记录唯一性。
  PRIMARY KEY (`id`),
  -- 唯一键防止幂等申请或授权票据重复。
  UNIQUE KEY `uk_data_export_job_request` (`user_id`, `request_key`),
  -- 按所属用户、状态或关联任务进行范围查询与清理。
  KEY `idx_data_export_job_month` (`user_id`, `quota_month`, `quota_charged`),
  -- 按所属用户、状态或关联任务进行范围查询与清理。
  KEY `idx_data_export_job_history` (`user_id`, `create_time`, `id`),
  -- 按所属用户、状态或关联任务进行范围查询与清理。
  KEY `idx_data_export_job_queue` (`status`, `create_time`, `id`),
  -- 按所属用户、状态或关联任务进行范围查询与清理。
  KEY `idx_data_export_job_expiry` (`artifact_status`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人数据导出任务与月额度账本';

-- 固定控制行只补缺失，不覆盖已有数据。
INSERT INTO `data_export_coordinator` (`id`, `name`) VALUES (1, 'global') ON DUPLICATE KEY UPDATE `id` = `id`;
