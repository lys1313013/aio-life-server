-- 目的：生活足迹保存所选地区编码，支持编辑时恢复省市区县乡镇路径。
-- 前置：MySQL 8.0.16+；life_footprint已存在，先执行system_region建表。
-- 影响：仅增加可空列；不推断或回填历史地区，不改变坐标及审计数据，旧客户端兼容。
-- 重跑：按实际列检测，已存在则跳过。
-- 执行：先迁移再部署新后端，遇错停止；新代码上线前旧应用仍可读写。
-- 恢复：DDL不可整体回滚；回退应用时保留可空列及新版所选编码。
SET @footprint_region_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='life_footprint' AND column_name='region_code');
SET @footprint_region_sql = IF(@footprint_region_exists=0,
  'ALTER TABLE `life_footprint` ADD COLUMN `region_code` VARCHAR(12) DEFAULT NULL COMMENT ''所选最深层地区编码，可停在省市区县或乡镇，未选及历史记录为空'' AFTER `city`', 'SELECT 1');
PREPARE footprint_region_stmt FROM @footprint_region_sql;
EXECUTE footprint_region_stmt;
DEALLOCATE PREPARE footprint_region_stmt;
