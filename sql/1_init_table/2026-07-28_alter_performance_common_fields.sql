-- performance 表公共字段对齐 BaseEntity 约定
-- 1. id 从 int 扩容为 bigint，适配雪花算法 ID（IdType.ASSIGN_ID）
-- 2. create_by/update_by 更名为 create_user/update_user 并扩容为 bigint（与 user.id 对齐）
-- 3. 新增 is_deleted 逻辑删除字段
-- 4. 删除 image_url 列，封面图改走 file 表（biz_type='performance'）统一存储
ALTER TABLE `performance` MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '唯一标识';
ALTER TABLE `performance` CHANGE COLUMN `create_by` `create_user` bigint DEFAULT NULL COMMENT '创建人ID';
ALTER TABLE `performance` CHANGE COLUMN `update_by` `update_user` bigint DEFAULT NULL COMMENT '更新人ID';
ALTER TABLE `performance` ADD COLUMN `is_deleted` tinyint DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除';
ALTER TABLE `performance` DROP COLUMN `image_url`;
