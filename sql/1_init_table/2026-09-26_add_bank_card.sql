-- 银行卡与标签关联。银行选择 sys_dict_data 或填写自定义名称；卡面通过 file.biz_type/biz_id 反向关联。
USE `aio_life`;
CREATE TABLE IF NOT EXISTS `bank_card` (
  `id` BIGINT NOT NULL COMMENT '雪花ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户',
  `bank_id` BIGINT DEFAULT NULL COMMENT 'sys_dict_data.dict_code，类型bank；自定义银行时为空',
  `custom_bank_name` VARCHAR(100) DEFAULT NULL COMMENT '用户填写的银行名称，仅bank_id为空时使用',
  `card_name` VARCHAR(100) DEFAULT NULL COMMENT '卡片产品名称',
  `alias` VARCHAR(50) DEFAULT NULL COMMENT '个人别名',
  `card_type` VARCHAR(20) NOT NULL COMMENT 'debit储蓄卡 credit信用卡',
  `card_no_ciphertext` VARCHAR(512) NOT NULL COMMENT '版本化SM4-GCM卡号密文',
  `card_no_fingerprint` BINARY(32) NOT NULL COMMENT '主密钥派生HMAC-SM3判重指纹',
  `card_no_last4` CHAR(4) NOT NULL COMMENT '卡号末四位',
  `branch_name` VARCHAR(200) DEFAULT NULL COMMENT '开户支行',
  `status` VARCHAR(20) NOT NULL DEFAULT 'normal' COMMENT 'normal frozen lost closed',
  `opened_date` DATE DEFAULT NULL COMMENT '开卡日期',
  `expiry_month` DATE DEFAULT NULL COMMENT '有效期月份，存当月第一天',
  `credit_limit` DECIMAL(18,2) DEFAULT NULL COMMENT '信用额度，人民币元；NULL未记录',
  `statement_day` TINYINT UNSIGNED DEFAULT NULL COMMENT '每月账单日1至31',
  `repayment_day` TINYINT UNSIGNED DEFAULT NULL COMMENT '每月固定还款日1至31',
  `cover_color` CHAR(7) DEFAULT NULL COMMENT '默认卡面Hex颜色',
  `cover_source_url` VARCHAR(1000) DEFAULT NULL COMMENT '当前卡面出处，不用于远程抓取',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序值，小值在前',
  `remark` VARCHAR(1000) DEFAULT NULL COMMENT '备注',
  `is_deleted` TINYINT NOT NULL DEFAULT 0,
  `create_user` BIGINT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_user` BIGINT NOT NULL,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_sort` (`user_id`,`is_deleted`,`sort_order`,`id`),
  KEY `idx_bank_reference` (`bank_id`,`is_deleted`),
  KEY `idx_user_bank` (`user_id`,`bank_id`,`is_deleted`),
  KEY `idx_user_fingerprint` (`user_id`,`card_no_fingerprint`,`is_deleted`),
  KEY `idx_user_last4` (`user_id`,`card_no_last4`,`is_deleted`),
  CONSTRAINT `chk_bank_card_bank_source` CHECK (
    (bank_id IS NOT NULL AND custom_bank_name IS NULL)
    OR (bank_id IS NULL AND custom_bank_name IS NOT NULL AND CHAR_LENGTH(TRIM(custom_bank_name)) > 0)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='银行卡档案';
CREATE TABLE IF NOT EXISTS `bank_card_tag_rel` (
  `id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL COMMENT '与银行卡和标签的所属用户一致',
  `bank_card_id` BIGINT NOT NULL,
  `tag_id` BIGINT NOT NULL COMMENT 'user_dict_data.id，类型bank_card_tag',
  `is_deleted` TINYINT NOT NULL DEFAULT 0,
  `create_user` BIGINT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_user` BIGINT NOT NULL,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_card_tag` (`user_id`,`bank_card_id`,`tag_id`,`is_deleted`),
  KEY `idx_user_tag_card` (`user_id`,`tag_id`,`is_deleted`,`bank_card_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='银行卡标签关联';
