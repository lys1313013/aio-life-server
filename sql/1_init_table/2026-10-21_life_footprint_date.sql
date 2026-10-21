-- 将早期试用表的起止时间改为 DATE；新建 DATE 表也可执行。
-- 执行前暂停生活足迹写入。遇错停止，禁止使用 mysql --force。
-- 年份、年月、零日期或非法日期必须先由用户确认完整日期，不能自动补齐。
SET @footprint_previous_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

-- 最新建表脚本已使用 DATE；只处理仍带旧状态字段的试用表。
SET @footprint_has_ongoing = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'life_footprint' AND column_name = 'ongoing');
SET @footprint_date_sql = IF(@footprint_has_ongoing > 0,
  'ALTER TABLE `life_footprint`
  DROP CHECK `ck_footprint_end`,
  MODIFY COLUMN `start_date` date NOT NULL COMMENT ''开始日期 YYYY-MM-DD'',
  MODIFY COLUMN `end_date` date DEFAULT NULL COMMENT ''结束日期，至今时为空'',
  ADD CONSTRAINT `ck_footprint_end` CHECK ((`ongoing` = 1 AND `end_date` IS NULL) OR (`ongoing` = 0 AND `end_date` IS NOT NULL AND `end_date` >= `start_date`))', 'SELECT 1');
PREPARE footprint_date_stmt FROM @footprint_date_sql;
EXECUTE footprint_date_stmt;
DEALLOCATE PREPARE footprint_date_stmt;

SET SESSION sql_mode = @footprint_previous_sql_mode;
