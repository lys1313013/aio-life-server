-- =====================================================
-- 对齐 time_record.category_id 类型：varchar(32) NOT NULL → bigint
--
-- 背景：category_id 实际存 time_tracker_category.id（bigint 主键的雪花数字串），
-- 却定义为 varchar(32)，join 走不到索引。见 docs/数据库表结构审查报告.md 2026-08-22
-- 复核 P1。实体 TimeRecordEntity.categoryId 已改为 Long，关联代码链路已同步。
--
-- 执行策略（三步，幂等）：
--   1) 先将 NOT NULL 放宽为 NULL —— 原列 NOT NULL，直接置 NULL 会报
--      'Column category_id cannot be null'，必须先允许空值。
--   2) 清洗：空串 / 非纯数字的历史值规范化为 NULL（NOT NULL 挡不住空串，
--      库存量可能残留非数字值；置 NULL 的脏数据会丢失分类关联，属预期清理）。
--   3) 类型对齐：仅当列仍为 varchar 时 MODIFY bigint。
--
-- 注意：新库（执行 2026-08-18_init_all_tables.sql）中该列已是 bigint NULL，
--       本脚本第 1/3 步会自动跳过，仅第 2 步清洗空跑。
-- =====================================================

USE `aio_life`;

-- 0) 诊断：执行前人工确认将有多少条非法值会被置 NULL
SELECT COUNT(*) AS illegal_category_id_count
FROM `time_record`
WHERE `category_id` IS NOT NULL
  AND (TRIM(`category_id`) = '' OR TRIM(`category_id`) NOT REGEXP '^[0-9]+$');

-- 1) 放宽 NOT NULL（仅当当前是 varchar 且 NOT NULL）
SET @s = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'aio_life' AND TABLE_NAME = 'time_record'
               AND COLUMN_NAME = 'category_id' AND DATA_TYPE = 'varchar' AND IS_NULLABLE = 'NO'),
    'ALTER TABLE `time_record` MODIFY COLUMN `category_id` varchar(32) NULL COMMENT ''分类ID''',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 2) 清洗非法值：空串 / 非纯数字的 category_id 规范化为 NULL
UPDATE `time_record`
SET `category_id` = NULL
WHERE `category_id` IS NOT NULL
  AND (TRIM(`category_id`) = '' OR TRIM(`category_id`) NOT REGEXP '^[0-9]+$');

-- 3) 类型对齐 time_record.category_id → time_tracker_category.id (bigint)
SET @s = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'aio_life' AND TABLE_NAME = 'time_record' AND COLUMN_NAME = 'category_id' AND DATA_TYPE = 'varchar'),
    'ALTER TABLE `time_record` MODIFY COLUMN `category_id` bigint DEFAULT NULL COMMENT ''分类ID(关联time_tracker_category.id)''',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;