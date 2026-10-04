-- 旧库只执行一次。先复制历史状态，再由两端独立维护；不得重复回填覆盖已配置的移动端状态。
USE `aio_life`;
ALTER TABLE `sys_menu`
    MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT 'Web端状态（1启用，0禁用）',
    ADD COLUMN `mobile_status` TINYINT NOT NULL DEFAULT 1 COMMENT '移动端状态（1启用，0禁用）' AFTER `status`;
UPDATE `sys_menu` SET `mobile_status` = `status`;
