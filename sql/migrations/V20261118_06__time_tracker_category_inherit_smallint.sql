-- 目的：把 time_tracker_category.inherit_parent 由 boolean 改为 smallint 0/1，与全局 NumericBooleanTypeHandler 和其他布尔状态列保持一致。
-- 前置：PostgreSQL 18+；已执行 V20261118_05，time_tracker_category.inherit_parent 仍为 boolean。
-- 影响：删除并按 0/1 语义重建 ck_time_tracker_category_inherit，转换列类型、默认值与注释；不改其他列、行数和其他约束。
-- 重跑：Flyway 成功后不重跑；重复执行会因列已为 smallint 而失败。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；失败事务回滚。
-- 验证：查询 information_schema.columns 确认 smallint、非空、默认 0，并核对 inherit_parent 分布与迁移前一致。

ALTER TABLE time_tracker_category DROP CONSTRAINT ck_time_tracker_category_inherit;
ALTER TABLE time_tracker_category ALTER COLUMN inherit_parent DROP DEFAULT;
ALTER TABLE time_tracker_category ALTER COLUMN inherit_parent TYPE smallint USING (CASE WHEN inherit_parent THEN 1 ELSE 0 END);
ALTER TABLE time_tracker_category ALTER COLUMN inherit_parent SET DEFAULT 0;
ALTER TABLE time_tracker_category ADD CONSTRAINT ck_time_tracker_category_inherit_parent CHECK (inherit_parent IN (0, 1));
ALTER TABLE time_tracker_category ADD CONSTRAINT ck_time_tracker_category_inherit CHECK (
    inherit_parent = 0 OR (template_id IS NOT NULL AND parent_id IS NULL)
);
COMMENT ON COLUMN time_tracker_category.inherit_parent IS '覆盖记录是否继承公共分类的父级（1 是，0 否）；非覆盖记录固定为 0';
COMMENT ON CONSTRAINT ck_time_tracker_category_inherit ON time_tracker_category IS '只有覆盖记录可以继承父级，且继承时不得同时保存本地父级';
COMMENT ON CONSTRAINT ck_time_tracker_category_inherit_parent ON time_tracker_category IS 'inherit_parent 只能为 0 或 1';