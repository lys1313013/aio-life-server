-- 个人菜单显示偏好：存在记录即隐藏，不影响角色权限和业务数据。
-- 保留统一 BaseEntity 字段；恢复显示/默认设置使用物理 DELETE。
CREATE TABLE IF NOT EXISTS `user_menu_hidden` (
    `id` bigint NOT NULL COMMENT '主键（雪花）',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `menu_id` bigint NOT NULL COMMENT '关联 sys_menu.id',
    `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '更新人ID',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `is_deleted` int NOT NULL DEFAULT 0 COMMENT '统一保留字段，固定为0，删除使用物理删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_menu` (`user_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户隐藏菜单';
