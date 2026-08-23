-- =====================================================
-- 删除 b_video 死字段 + 死索引：progress、added_at
--
-- 背景：两列实体未映射、代码零引用。progress 与 watched_duration/duration
--       语义重复（观看进度可由二者推导）；added_at 与审计列 create_time 重复。
--       last_watched 不在删除范围——已于 2026-08-22 补实体映射
--       （BVideoEntity.lastWatched），并在 syncProgress 接口写入。
--       见 docs/数据库表结构审查报告.md P2「b_video 死字段 + 死索引」。
--
-- 说明：
--   - 全量建表脚本（2026-08-18_init_all_tables.sql）已同步移除这两列及
--     idx_progress / idx_added_at，新库不需执行本脚本。
--   - MySQL DROP COLUMN 会自动从包含该列的索引中移除该列；单列索引随之删除，
--     故 idx_progress / idx_added_at 无需显式 DROP，此处仍显式处理以保幂等清晰。
--   - 本脚本幂等，可重复执行。
-- =====================================================

USE `aio_life`;

-- 1) 显式删除死索引（若存在）
SET @s = IF(
    EXISTS(SELECT 1 FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = 'aio_life' AND TABLE_NAME = 'b_video' AND INDEX_NAME = 'idx_progress'),
    'ALTER TABLE `b_video` DROP INDEX `idx_progress`',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF(
    EXISTS(SELECT 1 FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = 'aio_life' AND TABLE_NAME = 'b_video' AND INDEX_NAME = 'idx_added_at'),
    'ALTER TABLE `b_video` DROP INDEX `idx_added_at`',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 2) 删除死列 progress（若存在）
SET @s = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'aio_life' AND TABLE_NAME = 'b_video' AND COLUMN_NAME = 'progress'),
    'ALTER TABLE `b_video` DROP COLUMN `progress`',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 3) 删除死列 added_at（若存在）
SET @s = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'aio_life' AND TABLE_NAME = 'b_video' AND COLUMN_NAME = 'added_at'),
    'ALTER TABLE `b_video` DROP COLUMN `added_at`',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
