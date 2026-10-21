-- 先执行日期迁移；暂停足迹写入，客户端遇错停止，不使用 --force。
-- 保留记录 ID、版本、归属及审计信息；包含软删除记录的至今状态一并转换。
SET @footprint_previous_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';
SET @footprint_has_ongoing = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'life_footprint' AND column_name = 'ongoing');
SET @footprint_has_request = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'life_footprint' AND column_name = 'request_key');
SET @footprint_has_request_index = (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'life_footprint' AND index_name = 'uk_footprint_request');
SET @footprint_has_end_check = (SELECT COUNT(*) FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE() AND table_name = 'life_footprint' AND constraint_name = 'ck_footprint_end');

-- 旧约束只允许“至今”时结束日期为 NULL，先移除再转换。
SET @footprint_simplify_sql = IF(@footprint_has_end_check > 0,
  'ALTER TABLE `life_footprint` DROP CHECK `ck_footprint_end`', 'SELECT 1');
PREPARE footprint_simplify_stmt FROM @footprint_simplify_sql;
EXECUTE footprint_simplify_stmt;
DEALLOCATE PREPARE footprint_simplify_stmt;

SET @footprint_simplify_sql = IF(@footprint_has_ongoing > 0,
  'UPDATE `life_footprint` SET `end_date` = ''9999-12-31'' WHERE `ongoing` = 1', 'SELECT 1');
PREPARE footprint_simplify_stmt FROM @footprint_simplify_sql;
EXECUTE footprint_simplify_stmt;
DEALLOCATE PREPARE footprint_simplify_stmt;

SET @footprint_simplify_sql = CONCAT('ALTER TABLE `life_footprint` ',
  IF(@footprint_has_request_index > 0, 'DROP INDEX `uk_footprint_request`, ', ''),
  IF(@footprint_has_request > 0, 'DROP COLUMN `request_key`, ', ''),
  IF(@footprint_has_ongoing > 0, 'DROP COLUMN `ongoing`, ', ''),
  'MODIFY COLUMN `end_date` date NOT NULL COMMENT ''结束日期，9999-12-31 表示至今'', ',
  'ADD CONSTRAINT `ck_footprint_end` CHECK (`end_date` >= `start_date`)');
PREPARE footprint_simplify_stmt FROM @footprint_simplify_sql;
EXECUTE footprint_simplify_stmt;
DEALLOCATE PREPARE footprint_simplify_stmt;
SET SESSION sql_mode = @footprint_previous_sql_mode;
