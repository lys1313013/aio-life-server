-- =====================================================
-- 财务域一致性修复：
--   1) expense.user_id 收紧为 NOT NULL（对齐 income.user_id）
--   2) time_record.create_user 注释修正：「用户ID」→「创建人」
--
-- 背景（docs/数据库表结构审查报告.md）：
--   - P3：expense.user_id 可空 vs income.user_id NOT NULL，财务同域不一致。
--     写入路径已核实唯一（ExpController.insert/update/saveBatch 均显式 setUserId，
--     MCP/定时任务无 expense 写入），收紧不影响正常写入。
--   - P2：time_record.create_user DDL 注释写「用户ID」，与真实归属列 user_id
--     混淆，审计列应标注「创建人」。
--
-- 执行策略：
--   - 第 0 步诊断 SELECT 先人工确认存量 NULL 行数；有 NULL 时先人工补齐
--     （按 create_user 回填或置入默认用户），再执行第 1 步。
--   - 第 1 步仅在无 NULL 行时收紧；第 2 步注释修改无条件执行。
--   - 全量建表脚本已同步，新库不需执行本脚本。本脚本可重复执行。
-- =====================================================

USE `aio_life`;

-- 0) 诊断：确认存量 NULL 行数（预期为 0；若 >0 需先人工补齐再执行第 1 步）
SELECT COUNT(*) AS null_user_id_count
FROM `expense`
WHERE `user_id` IS NULL;

-- 1) 收紧 expense.user_id 为 NOT NULL（仅当列存在且当前允许 NULL 且无 NULL 数据时执行）
SET @s = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'aio_life' AND TABLE_NAME = 'expense'
               AND COLUMN_NAME = 'user_id' AND IS_NULLABLE = 'YES')
    AND NOT EXISTS(SELECT 1 FROM `expense` WHERE `user_id` IS NULL),
    'ALTER TABLE `expense` MODIFY COLUMN `user_id` bigint(20) NOT NULL COMMENT ''用户id''',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 2) 修正 time_record.create_user 注释：「用户ID」→「创建人」（仅当注释仍为旧值时执行）
SET @s = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'aio_life' AND TABLE_NAME = 'time_record'
               AND COLUMN_NAME = 'create_user' AND COLUMN_COMMENT = '用户ID'),
    'ALTER TABLE `time_record` MODIFY COLUMN `create_user` bigint(20) DEFAULT NULL COMMENT ''创建人''',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
