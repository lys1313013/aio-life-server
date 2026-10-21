-- 目的：生活足迹暂不使用乐观锁，移除version字段及对应CHECK约束。
-- 前置：MySQL 8.0.16+；按升级计划完成生活足迹建表及前序迁移。
-- 影响：删除版本字段，保留记录、用户归属及全部审计字段，不回填业务数据。
-- 重跑：按实际列和约束检测，已删除时跳过。
-- 执行：暂停旧应用写入，先迁移再部署不依赖version的后端和客户端；遇错停止。
-- 恢复：DDL不可整体回滚；执行前备份，不可仅回退到依赖version的旧应用。
SET @footprint_has_version = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'life_footprint' AND column_name = 'version');
SET @footprint_has_version_check = (SELECT COUNT(*) FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE() AND table_name = 'life_footprint' AND constraint_name = 'ck_footprint_version');
SET @footprint_drop_version_sql = IF(@footprint_has_version > 0,
  CONCAT('ALTER TABLE `life_footprint` ',
    IF(@footprint_has_version_check > 0, 'DROP CHECK `ck_footprint_version`, ', ''),
    'DROP COLUMN `version`'), 'SELECT 1');
PREPARE footprint_drop_version_stmt FROM @footprint_drop_version_sql;
EXECUTE footprint_drop_version_stmt;
DEALLOCATE PREPARE footprint_drop_version_stmt;
