-- 目的：补齐生活足迹表及全部字段的数据库注释。
-- 前置：MySQL 8.0.16+；已完成日期迁移及字段简化迁移。
-- 影响：仅修改注释，保留字段类型、可空性、默认值、索引和业务数据。
-- 重跑：可重复执行，结果相同；不恢复request_key或ongoing旧字段。
-- 执行：按upgrade-plan.json执行；ALTER可能持有元数据锁，遇错停止，禁止--force。
-- 恢复：DDL不可整体回滚；失败后核对SHOW CREATE TABLE，再修正或重跑。
ALTER TABLE `life_footprint`
  MODIFY COLUMN `id` BIGINT NOT NULL COMMENT '足迹ID，由应用生成雪花ID',
  MODIFY COLUMN `user_id` BIGINT NOT NULL COMMENT '所属用户ID，用于用户数据隔离',
  MODIFY COLUMN `start_date` DATE NOT NULL COMMENT '开始日期，精确到日，格式YYYY-MM-DD',
  MODIFY COLUMN `end_date` DATE NOT NULL COMMENT '结束日期，9999-12-31表示至今',
  MODIFY COLUMN `place_name` VARCHAR(100) NOT NULL COMMENT '地点名称，可填写住所名称或自定义名称',
  MODIFY COLUMN `address` VARCHAR(500) DEFAULT NULL COMMENT '详细地址，可补充楼栋和房号，未填写时为空',
  MODIFY COLUMN `city` VARCHAR(100) DEFAULT NULL COMMENT '地点所属城市名称，未获取时为空',
  MODIFY COLUMN `longitude` DECIMAL(10,6) NOT NULL COMMENT '经度，GCJ02坐标，单位度，范围-180至180',
  MODIFY COLUMN `latitude` DECIMAL(9,6) NOT NULL COMMENT '纬度，GCJ02坐标，单位度，范围-90至90',
  MODIFY COLUMN `coordinate_system` VARCHAR(16) NOT NULL DEFAULT 'GCJ02' COMMENT '坐标系，固定为GCJ02，写入前须完成坐标转换',
  MODIFY COLUMN `provider_uid` VARCHAR(128) DEFAULT NULL COMMENT '百度地点UID，手动选点或未获取时为空',
  MODIFY COLUMN `note` TEXT COMMENT '备注，应用限制5000字符，未填写时为空',
  MODIFY COLUMN `create_user` BIGINT DEFAULT NULL COMMENT '创建人用户ID，未记录时为空',
  MODIFY COLUMN `create_time` DATETIME DEFAULT NULL COMMENT '创建时间，由应用写入，未记录时为空',
  MODIFY COLUMN `update_user` BIGINT DEFAULT NULL COMMENT '最后更新人用户ID，未记录时为空',
  MODIFY COLUMN `update_time` DATETIME DEFAULT NULL COMMENT '最后更新时间，由应用写入，未记录时为空',
  MODIFY COLUMN `is_deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
  COMMENT = '生活足迹，记录长期居住地点';
