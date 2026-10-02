-- 卡号选填：已填写的卡号及其加密、判重数据保持不变。
USE `aio_life`;
ALTER TABLE `bank_card`
  MODIFY COLUMN `card_no_ciphertext` VARCHAR(512) DEFAULT NULL COMMENT '版本化SM4-GCM卡号密文',
  MODIFY COLUMN `card_no_fingerprint` BINARY(32) DEFAULT NULL COMMENT '主密钥派生HMAC-SM3判重指纹',
  MODIFY COLUMN `card_no_last4` CHAR(4) DEFAULT NULL COMMENT '卡号末四位';
