-- =====================================================
-- 删除 goal 冗余死列：progress
--
-- 背景：progress 与 current_value/target_value 语义重复（进度可由二者推导），
--       且 GoalEntity 已无 progress 字段、代码零引用，属死列。
--       见 docs/数据库表结构审查报告.md P2「goal 冗余列」。
--
-- 说明：
--   - 全量建表脚本（2026-08-18_init_all_tables.sql）已同步移除该列，
--     新库不需执行本脚本。
--   - 本脚本幂等，可重复执行。
-- =====================================================

USE `aio_life`;

-- 删除冗余列 goal.progress（若存在）
SET @s = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'aio_life' AND TABLE_NAME = 'goal' AND COLUMN_NAME = 'progress'),
    'ALTER TABLE `goal` DROP COLUMN `progress`',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
