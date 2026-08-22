-- =====================================================
-- 对齐 time_record.category_id 类型：varchar(32) → bigint(20)
--
-- 背景：category_id 实际存 time_tracker_category.id（bigint 主键的雪花数字串），
-- 却定义为 varchar(32)，join 走不到索引。见 docs/数据库表结构审查报告.md 2026-08-22
-- 复核 P1。实体 TimeRecordEntity.categoryId 已改为 Long，关联代码链路已同步。
--
-- 幂等：先清洗非数字值，再仅当列仍为 varchar 时 MODIFY，可重复执行。
-- 注意：非数字/空串的历史脏数据会被置 NULL（软删除记录无影响）。
-- =====================================================

USE `aio-life`;

-- 1) 清洗非法值：空串 / 非纯数字的 category_id 规范化为 NULL
UPDATE `time_record`
SET `category_id` = NULL
WHERE `category_id` IS NOT NULL
  AND (TRIM(`category_id`) = '' OR TRIM(`category_id`) NOT REGEXP '^[0-9]+$');

-- 2) 类型对齐 time_record.category_id → time_tracker_category.id (bigint)
SET @s = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'aio-life' AND TABLE_NAME = 'time_record' AND COLUMN_NAME = 'category_id' AND DATA_TYPE = 'varchar'),
    'ALTER TABLE `time_record` MODIFY COLUMN `category_id` bigint(20) DEFAULT NULL COMMENT ''分类ID(关联time_tracker_category.id)''',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;