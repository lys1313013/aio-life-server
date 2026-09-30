-- 微信小程序手机号登录：仅升级尚未执行本迁移的已有数据库。
-- 新数据库执行全量初始化脚本即可，不要再次执行本文件。
-- 保留现有用户所有数据，不为历史用户填充手机号、区号或微信身份。
ALTER TABLE `user`
    MODIFY COLUMN `password` varchar(255) DEFAULT NULL COMMENT '密码，NULL表示未设置',
    ADD COLUMN `phone_country_code` varchar(5) DEFAULT NULL COMMENT '国际电话区号，不含加号',
    ADD COLUMN `phone` varchar(20) DEFAULT NULL COMMENT '不含国际区号的手机号',
    ADD COLUMN `phone_verified_at` datetime DEFAULT NULL COMMENT '最近接收可信手机号验证结果的时间',
    ADD COLUMN `wechat_openid` varchar(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '当前配置小程序的openid',
    ADD COLUMN `wechat_unionid` varchar(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '微信unionid，可为空，不用于自动合并账号',
    ADD COLUMN `active_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `is_deleted` = 0 THEN 1 ELSE NULL END) STORED COMMENT '仅有效用户参与凭证唯一约束',
    ADD UNIQUE KEY `uk_user_phone_active` (`phone_country_code`, `phone`, `active_flag`),
    ADD UNIQUE KEY `uk_user_wechat_active` (`wechat_openid`, `active_flag`),
    ADD CONSTRAINT `ck_user_phone_pair` CHECK (
        (`phone_country_code` IS NULL AND `phone` IS NULL) OR
        (`phone_country_code` IS NOT NULL AND `phone` IS NOT NULL
         AND `phone_country_code` REGEXP '^[1-9][0-9]{0,2}$'
         AND `phone` REGEXP '^[0-9]{4,14}$'
         AND CHAR_LENGTH(`phone_country_code`) + CHAR_LENGTH(`phone`) <= 15)
    );
