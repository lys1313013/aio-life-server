-- =====================================================
-- 演出表 performance：create_by/update_by (int) → create_user/update_user (bigint)
--
-- 背景：2026-08-19 的 unify_audit_columns.sql 统一了 user 的 created_at/updated_at、
-- sys_dict_type / sys_dict_data 的 create_by/update_by(varchar)，但漏掉了 performance
-- （旧建表脚本 2026-03-11 中它是 create_by int/update_by int）。本脚本补上这一缺口。
--
-- 幂等：可从任意库状态执行，不会因重复执行报错。
-- =====================================================

USE `aio_life`;

-- 1) 补齐 create_user / update_user（仅当 create_by 仍存在且 create_user 不存在时）
SET @s = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'aio_life' AND TABLE_NAME = 'performance' AND COLUMN_NAME = 'create_by')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'aio_life' AND TABLE_NAME = 'performance' AND COLUMN_NAME = 'create_user'),
    'ALTER TABLE `performance`
        ADD COLUMN `create_user` bigint(20) DEFAULT NULL COMMENT ''创建人ID'' AFTER `order_number`,
        ADD COLUMN `update_user` bigint(20) DEFAULT NULL COMMENT ''更新人ID'' AFTER `create_time`',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 2) 迁移数据（create_by/update_by 为 int，可直接无损赋值）
SET @s = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'aio_life' AND TABLE_NAME = 'performance' AND COLUMN_NAME = 'create_by')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'aio_life' AND TABLE_NAME = 'performance' AND COLUMN_NAME = 'create_user'),
    'UPDATE `performance` SET `create_user` = `create_by`, `update_user` = `update_by`',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 3) 删除旧列
SET @s = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'aio_life' AND TABLE_NAME = 'performance' AND COLUMN_NAME = 'create_by'),
    'ALTER TABLE `performance` DROP COLUMN `create_by`, DROP COLUMN `update_by`',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- =====================================================
-- 可选兜底：放宽所有业务表残留的 NOT NULL create_user/update_user
-- （user_bind、api_key、chat_message、file 等若 08-19 unify 未执行过会命中；执行过则无操作）
-- 豁免表：api_key_log / login_log / mail_log（日志表）、enum_type（枚举定义表）
-- =====================================================
DELIMITER $$
DROP PROCEDURE IF EXISTS `relax_not_null_audit`$$
CREATE PROCEDURE `relax_not_null_audit`()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE t VARCHAR(128);
    DECLARE cur CURSOR FOR
        SELECT TABLE_NAME FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = 'aio_life'
          AND TABLE_NAME NOT IN ('api_key_log', 'login_log', 'mail_log', 'enum_type')
          AND EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                       WHERE c.TABLE_SCHEMA = 'aio_life' AND c.TABLE_NAME = information_schema.TABLES.TABLE_NAME
                         AND c.COLUMN_NAME IN ('create_user', 'update_user') AND c.IS_NULLABLE = 'NO');
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;
    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO t;
        IF done THEN LEAVE read_loop; END IF;

        SET @s = IF(
            EXISTS(SELECT 1 FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = 'aio_life' AND TABLE_NAME = t AND COLUMN_NAME = 'create_user' AND IS_NULLABLE = 'NO'),
            CONCAT('ALTER TABLE `', t, '` MODIFY COLUMN `create_user` bigint DEFAULT NULL COMMENT ''创建人'''),
            'SELECT 1');
        PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

        SET @s = IF(
            EXISTS(SELECT 1 FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = 'aio_life' AND TABLE_NAME = t AND COLUMN_NAME = 'update_user' AND IS_NULLABLE = 'NO'),
            CONCAT('ALTER TABLE `', t, '` MODIFY COLUMN `update_user` bigint DEFAULT NULL COMMENT ''更新人'''),
            'SELECT 1');
        PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END LOOP;
    CLOSE cur;
END$$
DELIMITER ;

CALL `relax_not_null_audit`();
DROP PROCEDURE IF EXISTS `relax_not_null_audit`;
