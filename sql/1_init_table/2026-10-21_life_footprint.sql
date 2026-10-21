-- 目的：创建生活足迹表，记录长期居住地点及起止日期。
-- 前置：MySQL 8.0.16+；由调用方选择目标数据库。
-- 影响：仅创建缺失表，不写入业务或演示数据。
-- 重跑：已有表会跳过，不自动修改已有结构或注释。
-- 执行：按升级计划执行；已有旧表继续执行日期、字段简化及注释迁移。
-- 恢复：遇错停止并核对实际结构；不自动删除已创建的表。
CREATE TABLE IF NOT EXISTS `life_footprint` (
  `id` BIGINT NOT NULL COMMENT '足迹ID，由应用生成雪花ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID，用于用户数据隔离',
  `start_date` DATE NOT NULL COMMENT '开始日期，精确到日，格式YYYY-MM-DD',
  `end_date` DATE NOT NULL COMMENT '结束日期，9999-12-31表示至今',
  `place_name` VARCHAR(100) NOT NULL COMMENT '地点名称，可填写住所名称或自定义名称',
  `address` VARCHAR(500) DEFAULT NULL COMMENT '详细地址，可补充楼栋和房号，未填写时为空',
  `city` VARCHAR(100) DEFAULT NULL COMMENT '地点所属城市名称，未获取时为空',
  `longitude` DECIMAL(10,6) NOT NULL COMMENT '经度，GCJ02坐标，单位度，范围-180至180',
  `latitude` DECIMAL(9,6) NOT NULL COMMENT '纬度，GCJ02坐标，单位度，范围-90至90',
  `coordinate_system` VARCHAR(16) NOT NULL DEFAULT 'GCJ02' COMMENT '坐标系，固定为GCJ02，写入前须完成坐标转换',
  `provider_uid` VARCHAR(128) DEFAULT NULL COMMENT '百度地点UID，手动选点或未获取时为空',
  `note` TEXT COMMENT '备注，应用限制5000字符，未填写时为空',
  `create_user` BIGINT DEFAULT NULL COMMENT '创建人用户ID，未记录时为空',
  `create_time` DATETIME DEFAULT NULL COMMENT '创建时间，由应用写入，未记录时为空',
  `update_user` BIGINT DEFAULT NULL COMMENT '最后更新人用户ID，未记录时为空',
  `update_time` DATETIME DEFAULT NULL COMMENT '最后更新时间，由应用写入，未记录时为空',
  `is_deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
  -- 应用生成的足迹ID全表唯一。
  PRIMARY KEY (`id`),
  -- 按用户和删除状态筛选，并按开始日期排序读取足迹。
  KEY `idx_footprint_user_start` (`user_id`, `is_deleted`, `start_date`),
  -- 统一存储GCJ02坐标，避免跨端地图坐标系混用。
  CONSTRAINT `ck_footprint_coordinate` CHECK (`coordinate_system` = 'GCJ02'),
  -- 经度必须处于有效范围，单位为度。
  CONSTRAINT `ck_footprint_lng` CHECK (`longitude` BETWEEN -180 AND 180),
  -- 纬度必须处于有效范围，单位为度。
  CONSTRAINT `ck_footprint_lat` CHECK (`latitude` BETWEEN -90 AND 90),
  -- 结束日期不得早于开始日期；9999-12-31表示至今。
  CONSTRAINT `ck_footprint_end` CHECK (`end_date` >= `start_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生活足迹，记录长期居住地点';
