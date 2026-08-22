-- =====================================================
-- 对齐聚合表外键列类型：int(11) → bigint(20)
--
-- 背景：目标表主键（task_column.id / user_dict_data.id）均为 bigint(20)，
-- 消费方 task.column_id、expense.exp_type_id、income.inc_type_id 却为 int(11)，
-- join 走不到索引 / 触发隐式转换。见 docs/数据库表结构审查报告.md 2026-08-22 复核 P1。
-- 实体字段已是 Long，本脚本仅改 DDL。
--
-- 幂等：仅当列当前为 int 类型时执行 MODIFY，可重复执行。
-- =====================================================

USE `aio_life`;

-- 1) task.column_id → task_column.id (bigint)
SET @s = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'aio_life' AND TABLE_NAME = 'task' AND COLUMN_NAME = 'column_id' AND DATA_TYPE = 'int'),
    'ALTER TABLE `task` MODIFY COLUMN `column_id` bigint DEFAULT NULL',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 2) expense.exp_type_id → user_dict_data.id (bigint)
SET @s = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'aio_life' AND TABLE_NAME = 'expense' AND COLUMN_NAME = 'exp_type_id' AND DATA_TYPE = 'int'),
    'ALTER TABLE `expense` MODIFY COLUMN `exp_type_id` bigint NOT NULL COMMENT ''支出类型ID''',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 3) income.inc_type_id → user_dict_data.id (bigint)
SET @s = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'aio_life' AND TABLE_NAME = 'income' AND COLUMN_NAME = 'inc_type_id' AND DATA_TYPE = 'int'),
    'ALTER TABLE `income` MODIFY COLUMN `inc_type_id` bigint NOT NULL COMMENT ''收入类型''',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;