-- 会员记录表
CREATE TABLE IF NOT EXISTS `membership_record` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `name` VARCHAR(100) NOT NULL COMMENT '会员名称',
    `category` VARCHAR(50) NOT NULL DEFAULT 'other' COMMENT '分类:video/music/shopping/cloud/study/game/other',
    `provider` VARCHAR(100) COMMENT '平台/服务商',
    `icon` VARCHAR(50) COMMENT 'emoji图标',
    `color` VARCHAR(50) COMMENT '卡片背景色class',
    `start_date` DATE COMMENT '开通日期',
    `expiry_date` DATE NOT NULL COMMENT '到期日期',
    `price` DECIMAL(10,2) COMMENT '支付金额',
    `auto_renew` TINYINT DEFAULT 0 COMMENT '是否自动续费:0-否 1-是',
    `note` VARCHAR(500) COMMENT '备注',
    `create_user` BIGINT COMMENT '创建人',
    `create_time` DATETIME COMMENT '创建时间',
    `update_user` BIGINT COMMENT '更新人',
    `update_time` DATETIME COMMENT '更新时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_expiry_date` (`expiry_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员记录表';

-- 添加会员菜单（在物品中心分组下）
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `path`, `component`, `redirect`, `meta`, `roles`, `sort`, `status`, `is_deleted`)
VALUES
(1703, 1700, 'Membership', '/membership', 'membership/index', NULL,
 JSON_OBJECT('icon','ant-design:gift-outlined','title','会员'),
 NULL, 2, 1, 0);
