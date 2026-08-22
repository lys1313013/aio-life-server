-- =====================================================
-- 统一 income 主键命名：income_id → id
--
-- 背景：同域 expense 主键为 id，income 却用 income_id（命名孤立，见
-- docs/数据库表结构审查报告.md 2026-08-22 复核结论 P2-9）。实体也由
-- incomeId 改为 id，全量建表脚本已同步。
--
-- 幂等：可从任意库状态执行，不会因重复执行报错。
-- =====================================================

USE `aio-life`;

SET @s = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'aio-life' AND TABLE_NAME = 'income' AND COLUMN_NAME = 'income_id'),
    'ALTER TABLE `income` CHANGE COLUMN `income_id` `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT ''主键ID''',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;