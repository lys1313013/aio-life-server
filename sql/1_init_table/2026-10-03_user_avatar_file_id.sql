-- 不迁移旧头像，删除旧 URL 列后由用户重新上传头像。上线前备份并协调三端发布。
-- 显式匹配 file.id 的字符集与排序规则，避免继承旧 user 表的 utf8mb4_general_ci。
ALTER TABLE `user`
    DROP COLUMN `avatar`,
    ADD COLUMN `avatar_file_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像文件ID',
    ADD KEY `idx_user_avatar_file` (`avatar_file_id`),
    ADD CONSTRAINT `fk_user_avatar_file` FOREIGN KEY (`avatar_file_id`) REFERENCES `file` (`id`) ON DELETE RESTRICT;
