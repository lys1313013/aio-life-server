-- 目的：新增轻量定时任务配置和执行日志。
-- 前置：MySQL 5.7.44 或 8.0；选择目标库，停止旧版本 LeetCode 注解调度后再启用新版本。
-- 影响：仅创建两张新表；不回填业务数据，应用首次启动按旧配置补齐两个内置任务。
-- 重跑：仅适用于新表创建，已有同名表不修改；若结构不符必须另做 ALTER，不能靠重跑修复。
-- 执行：按 upgrade-plan.json 顺序执行，遇错停止；新库直接执行全量结构。
-- 恢复：DDL 无法整体回滚；失败后检查实际表结构。应用回退保留表和日志，避免新旧调度同时运行。

CREATE TABLE IF NOT EXISTS `sys_scheduled_job` (
  `id` BIGINT NOT NULL COMMENT '任务ID，由应用生成雪花ID',
  `job_code` VARCHAR(80) NOT NULL COMMENT '代码注册的任务编码，不允许任意脚本或方法名',
  `name` VARCHAR(100) NOT NULL COMMENT '任务名称',
  `cron` VARCHAR(100) NOT NULL COMMENT '六段Spring Cron，秒为单个整数，每分钟最多一次',
  `zone_id` VARCHAR(40) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '调度时区，首版固定Asia/Shanghai',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '自动调度开关：0=暂停，1=启用；不限制手动执行',
  `revision` INT NOT NULL DEFAULT 0 COMMENT '配置版本，从0开始，每次修改递增',
  `create_user` BIGINT DEFAULT NULL COMMENT '创建人用户ID，自动任务为空',
  `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
  `update_user` BIGINT DEFAULT NULL COMMENT '最后更新人用户ID，自动任务为空',
  `update_time` DATETIME DEFAULT NULL COMMENT '最后更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
  -- 雪花主键标识任务。
  PRIMARY KEY (`id`),
  -- 编码全表唯一，含软删除记录，启动初始化不得恢复已删除配置。
  UNIQUE KEY `uk_sys_scheduled_job_code` (`job_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可配置定时任务';

CREATE TABLE IF NOT EXISTS `sys_scheduled_job_log` (
  `id` BIGINT NOT NULL COMMENT '执行日志ID，由应用生成雪花ID',
  `job_id` BIGINT NOT NULL COMMENT '所属定时任务ID',
  `trigger_key` VARCHAR(80) NOT NULL COMMENT '自动执行为AUTO加计划时点毫秒值，手动及跳过记录为UUID',
  `trigger_type` VARCHAR(10) NOT NULL COMMENT '触发类型：AUTO=定时，MANUAL=手动',
  `status` VARCHAR(20) NOT NULL COMMENT '状态：RUNNING=执行中，SUCCESS=方法完成，FAILED=异常，SKIPPED=跳过，INTERRUPTED=结果未知',
  `scheduled_time` DATETIME DEFAULT NULL COMMENT '计划触发时间，Asia/Shanghai，手动触发为空',
  `start_time` DATETIME NOT NULL COMMENT '执行或跳过时间，Asia/Shanghai',
  `end_time` DATETIME DEFAULT NULL COMMENT '完成或检测中断时间，Asia/Shanghai，执行中为空',
  `duration_ms` BIGINT DEFAULT NULL COMMENT '执行耗时毫秒，0=不足1毫秒或跳过，中断及执行中为空',
  `message` VARCHAR(1000) DEFAULT NULL COMMENT '执行结果摘要，不存储外部响应或敏感参数，执行中为空',
  `instance_id` VARCHAR(36) NOT NULL COMMENT '执行服务实例启动时生成的UUID',
  `create_user` BIGINT DEFAULT NULL COMMENT '创建人用户ID，自动任务为空',
  `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
  `update_user` BIGINT DEFAULT NULL COMMENT '最后更新人用户ID，自动任务为空',
  `update_time` DATETIME DEFAULT NULL COMMENT '最后更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
  -- 雪花主键标识执行日志。
  PRIMARY KEY (`id`),
  -- 自动触发同一任务同一时点只允许一条执行记录，软删除也不重放。
  UNIQUE KEY `uk_sys_scheduled_job_log_trigger` (`job_id`, `trigger_key`),
  -- 按任务读取未删除日志，按执行时间和ID倒序分页。
  KEY `idx_sys_scheduled_job_log_page` (`job_id`, `is_deleted`, `start_time`, `id`),
  -- 执行锁空闲时查找遗留执行状态。
  KEY `idx_sys_scheduled_job_log_status` (`job_id`, `is_deleted`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时任务执行日志';
