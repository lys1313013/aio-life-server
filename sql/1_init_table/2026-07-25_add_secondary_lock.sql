-- 二级锁功能：为 user 添加二级密码字段
ALTER TABLE user ADD COLUMN secondary_password VARCHAR(128) COMMENT '二级密码（加盐哈希）';
ALTER TABLE user ADD COLUMN secondary_password_salt VARCHAR(64) COMMENT '二级密码盐值';

-- 二级锁功能：用户敏感菜单关联表
CREATE TABLE `user_secondary_lock_menu` (
    `id` BIGINT NOT NULL COMMENT '主键（雪花）',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    `create_user` BIGINT DEFAULT NULL COMMENT '创建人ID',
    `update_user` BIGINT DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户二级锁菜单关联';
