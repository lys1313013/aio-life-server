-- 演出归属与创建人分离。兼容 MySQL 5.7 / 8.0，仅执行一次。
-- 发布前暂停演出写入并备份；先执行 SELECT，存在 NULL 时人工确认归属后再迁移。
-- 包含软删除记录，保留创建人、修改人和原始审计时间。
-- 必须使用遇错停止的客户端执行（禁止 mysql --force）。DDL 非事务性；中断后核实进度再恢复。
SELECT `id` FROM `performance` WHERE `create_user` IS NULL;

SET @performance_previous_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = CONCAT_WS(',', NULLIF(@@SESSION.sql_mode, ''), 'STRICT_ALL_TABLES');

ALTER TABLE `performance`
    ADD COLUMN `user_id` bigint DEFAULT NULL COMMENT '所属用户ID' AFTER `id`;

UPDATE `performance`
SET `user_id` = `create_user`, `update_time` = `update_time`
WHERE `user_id` IS NULL;

-- 严格模式下有无法回填的 NULL 会明确失败，不会静默生成 0 归属。
ALTER TABLE `performance`
    MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '所属用户ID',
    ADD INDEX `idx_performance_user_deleted` (`user_id`, `is_deleted`, `id`);

SET SESSION sql_mode = @performance_previous_sql_mode;
