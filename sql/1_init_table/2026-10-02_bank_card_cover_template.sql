USE `aio_life`;
CREATE TABLE IF NOT EXISTS `bank_card_cover_template` (
  `id` BIGINT NOT NULL COMMENT '雪花ID',
  `name` VARCHAR(100) NOT NULL COMMENT '卡面名称',
  `bank_id` BIGINT NOT NULL COMMENT 'sys_dict_data.dict_code，类型bank',
  `card_type` VARCHAR(20) NOT NULL COMMENT 'debit借记卡 credit信用卡',
  `source_url` VARCHAR(1000) DEFAULT NULL COMMENT '图片出处',
  `is_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用，0-停用',
  `sort_order` INT NOT NULL DEFAULT 0,
  `is_deleted` TINYINT NOT NULL DEFAULT 0,
  `create_user` BIGINT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_user` BIGINT NOT NULL,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_bank_type_enabled` (`bank_id`,`card_type`,`is_deleted`,`is_enabled`,`sort_order`,`id`),
  CONSTRAINT `chk_cover_template_type` CHECK (`card_type` IN ('debit','credit')),
  CONSTRAINT `chk_cover_template_enabled` CHECK (`is_enabled` IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公共银行卡卡面';

-- 兼容全量初始化与已有数据库；重复执行不会重复添加列或索引。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bank_card' AND COLUMN_NAME='cover_template_id')=0,
  'ALTER TABLE bank_card ADD COLUMN cover_template_id BIGINT DEFAULT NULL COMMENT ''公共卡面ID''', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bank_card' AND INDEX_NAME='idx_cover_template_reference')=0,
  'ALTER TABLE bank_card ADD INDEX idx_cover_template_reference (cover_template_id,is_deleted)', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
