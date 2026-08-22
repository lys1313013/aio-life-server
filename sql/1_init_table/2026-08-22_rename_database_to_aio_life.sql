-- =====================================================
-- 数据库重命名：`aio-life` → `aio_life`
--
-- 背景：库名含连字符（-）属于 MySQL 必须引用字符，所有语句均需反引号包围，
--       GRANT 等语句漏写反引号会直接报语法错。统一改为下划线命名。
--       应用配置（application.yml）已同步改为 username/url = aio_life。
--
-- 说明：MySQL 8 不支持 RENAME DATABASE，本脚本逐表 RENAME TABLE 迁移：
--   - 单表原子操作，外键与触发器随表迁移
--   - 视图 / 存储过程 / 事件不随表迁移（当前库未使用，如有需单独重建）
-- 幂等：可从任意库状态执行，旧库不存在或已迁完时为无操作，重复执行安全。
-- 注意：执行前建议备份；执行期间应用应停机，完成后按文末说明处理账号再启动。
-- =====================================================

CREATE DATABASE IF NOT EXISTS `aio_life` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `aio_life`;

DELIMITER $$
DROP PROCEDURE IF EXISTS `rename_database_from_aio_life`$$
CREATE PROCEDURE `rename_database_from_aio_life`()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE t VARCHAR(128);
    DECLARE cur CURSOR FOR SELECT t FROM `_tables_to_rename`;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    -- 先固化旧库表清单，避免边遍历 information_schema 边 DDL 的不确定行为
    CREATE TEMPORARY TABLE IF NOT EXISTS `_tables_to_rename` (t VARCHAR(128) PRIMARY KEY);
    DELETE FROM `_tables_to_rename`;
    INSERT IGNORE INTO `_tables_to_rename` (t)
        SELECT TABLE_NAME FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = 'aio-life' AND TABLE_TYPE = 'BASE TABLE';

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO t;
        IF done THEN LEAVE read_loop; END IF;
        SET @s = CONCAT('RENAME TABLE `aio-life`.`', t, '` TO `aio_life`.`', t, '`');
        PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END LOOP;
    CLOSE cur;
END$$
DELIMITER ;

CALL `rename_database_from_aio_life`();
DROP PROCEDURE IF EXISTS `rename_database_from_aio_life`;

-- =====================================================
-- 后续手动步骤：
-- 1. 重建应用账号并授权（用户名与 application.yml 保持一致）：
--      CREATE USER 'aio_life'@'%' IDENTIFIED BY '<数据库密码>';
--      GRANT ALL PRIVILEGES ON `aio_life`.* TO 'aio_life'@'%';
--      DROP USER 'aio-life'@'%';
-- 2. 验证应用正常后删除旧库：
--      DROP DATABASE IF EXISTS `aio-life`;
-- =====================================================
