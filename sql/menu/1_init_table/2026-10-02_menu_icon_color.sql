-- 独立菜单图标颜色字段；兼容已执行全量建表脚本的数据库，可重复执行。
USE `aio_life`;
SET @menu_icon_color_sql = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_menu' AND COLUMN_NAME = 'icon_color'),
  'SELECT 1',
  'ALTER TABLE `sys_menu` ADD COLUMN `icon_color` VARCHAR(7) DEFAULT NULL COMMENT ''菜单图标颜色（#RRGGBB，空为默认）'' AFTER `redirect`'
);
PREPARE menu_icon_color_stmt FROM @menu_icon_color_sql;
EXECUTE menu_icon_color_stmt;
DEALLOCATE PREPARE menu_icon_color_stmt;
