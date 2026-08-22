-- =====================================================
-- 对齐 exercise_record.exercise_type_id 类型：varchar(50) NOT NULL → bigint
--
-- 背景：运动类型原引用 user_dict_data（dict_type='exercise_type'）的 dict_value，
--       而其它字典消费方（expense.exp_type_id / income.inc_type_id / expense.pay_type_id）
--       均以 bigint 主键关联 user_dict_data.id，且后端 dashboard 读取端也是按
--       user_dict_data.id 匹配 —— 本列存 dict_value 属关联偏离。见
--       docs/数据库表结构审查报告.md 2026-08-22 P1。
--       实体 ExerciseRecordEntity.exerciseTypeId 已改为 Long，代码链路已同步。
--
-- 执行策略（三步，幂等）与本日 category_id 脚本一致：
--   1) 先将 NOT NULL 放宽为 NULL —— 原列 NOT NULL，直接置 NULL / MODIFY 会报
--      'Column 'exercise_type_id' cannot be null'。
--   2) 清洗：空串 / 非纯数字的历史值规范化为 NULL（存量若由 mobile 端按
--      dictValue 写入会残留此类值；置 NULL 会丢失类型关联，属预期清理）。
--   3) 类型对齐：仅当列仍为 varchar 时 MODIFY bigint。
--
-- 注意：新库（执行 2026-08-18_init_all_tables.sql）中该列已是 bigint NULL，
--       本脚本第 1/3 步会自动跳过，仅第 2 步清洗空跑。
-- =====================================================

USE `aio-life`;

-- 0) 诊断：执行前人工确认将有多少条非法值会被置 NULL
SELECT COUNT(*) AS illegal_exercise_type_id_count
FROM `exercise_record`
WHERE `exercise_type_id` IS NOT NULL
  AND (TRIM(`exercise_type_id`) = '' OR TRIM(`exercise_type_id`) NOT REGEXP '^[0-9]+$');

-- 1) 放宽 NOT NULL（仅当当前是 varchar 且 NOT NULL）
SET @s = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'aio-life' AND TABLE_NAME = 'exercise_record'
               AND COLUMN_NAME = 'exercise_type_id' AND DATA_TYPE = 'varchar' AND IS_NULLABLE = 'NO'),
    'ALTER TABLE `exercise_record` MODIFY COLUMN `exercise_type_id` varchar(50) NULL COMMENT ''运动类型''',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 2) 清洗非法值：空串 / 非纯数字的 exercise_type_id 规范化为 NULL
UPDATE `exercise_record`
SET `exercise_type_id` = NULL
WHERE `exercise_type_id` IS NOT NULL
  AND (TRIM(`exercise_type_id`) = '' OR TRIM(`exercise_type_id`) NOT REGEXP '^[0-9]+$');

-- 3) 类型对齐 exercise_record.exercise_type_id → user_dict_data.id (bigint)
SET @s = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'aio-life' AND TABLE_NAME = 'exercise_record' AND COLUMN_NAME = 'exercise_type_id' AND DATA_TYPE = 'varchar'),
    'ALTER TABLE `exercise_record` MODIFY COLUMN `exercise_type_id` bigint DEFAULT NULL COMMENT ''运动类型ID(关联user_dict_data.id)''',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;