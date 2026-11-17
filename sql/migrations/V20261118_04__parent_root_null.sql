-- 目的：统一层级根节点的表示为 NULL，移除 0 / '0' 哨兵；时迹分类用独立布尔列表示覆盖记录是否继承模板父级。
-- 前置：PostgreSQL 18+；已执行 V20261118_03；执行前停写并备份数据库。
-- 影响：sys_menu、wardrobe_category 的 parent_id 根值由 0 改为 NULL；sys_region.parent_code 取消 NOT NULL，省级根值 '0' 改为 NULL；time_tracker_category 新增 inherit_parent，覆盖记录的 NULL 父级迁移为继承，0 级根迁移为 NULL；补充自引用与层级一致性约束。
-- 重跑：Flyway 只执行一次；数据修正与 DDL 在同一事务执行，失败整体回滚后修正原因再迁移。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；遇错停止。

SET LOCAL lock_timeout = '10s';

-- sys_menu：根 = NULL
UPDATE sys_menu SET parent_id = NULL WHERE parent_id = 0;
ALTER TABLE sys_menu ALTER COLUMN parent_id DROP DEFAULT;
ALTER TABLE sys_menu ADD CONSTRAINT ck_sys_menu_parent_root CHECK (parent_id IS NULL OR parent_id > 0);
ALTER TABLE sys_menu ADD CONSTRAINT ck_sys_menu_parent_self CHECK (parent_id <> id);
COMMENT ON COLUMN sys_menu.parent_id IS '父级菜单ID；NULL 表示根节点';
COMMENT ON CONSTRAINT ck_sys_menu_parent_root ON sys_menu IS '根节点使用 NULL，父级ID必须为正数';
COMMENT ON CONSTRAINT ck_sys_menu_parent_self ON sys_menu IS '菜单不能以自身为父级';

-- wardrobe_category：根 = NULL，清除历史 0 值
UPDATE wardrobe_category SET parent_id = NULL WHERE parent_id = 0;
ALTER TABLE wardrobe_category ADD CONSTRAINT ck_wardrobe_category_parent_root CHECK (parent_id IS NULL OR parent_id > 0);
ALTER TABLE wardrobe_category ADD CONSTRAINT ck_wardrobe_category_parent_self CHECK (parent_id <> id);
COMMENT ON COLUMN wardrobe_category.parent_id IS '父分类ID；NULL 表示根分类';
COMMENT ON CONSTRAINT ck_wardrobe_category_parent_root ON wardrobe_category IS '根分类使用 NULL，父级ID必须为正数';
COMMENT ON CONSTRAINT ck_wardrobe_category_parent_self ON wardrobe_category IS '分类不能以自身为父级';

-- sys_region：根 = NULL，层级与根值保持一致
ALTER TABLE sys_region ALTER COLUMN parent_code DROP NOT NULL;
UPDATE sys_region SET parent_code = NULL WHERE parent_code = '0';
ALTER TABLE sys_region ADD CONSTRAINT ck_sys_region_parent_root CHECK (parent_code IS NULL OR parent_code <> '0');
ALTER TABLE sys_region ADD CONSTRAINT ck_sys_region_parent_self CHECK (parent_code <> code);
ALTER TABLE sys_region ADD CONSTRAINT ck_sys_region_parent_level CHECK ((level = 1) = (parent_code IS NULL));
COMMENT ON COLUMN sys_region.parent_code IS '上级地区编码；NULL 表示省级根节点';
COMMENT ON CONSTRAINT ck_sys_region_parent_root ON sys_region IS '根节点使用 NULL，不再使用字符串 0';
COMMENT ON CONSTRAINT ck_sys_region_parent_self ON sys_region IS '地区不能以自身为上级';
COMMENT ON CONSTRAINT ck_sys_region_parent_level ON sys_region IS '省级（level=1）没有上级，其余层级必须有上级';

-- time_tracker_category：根 = NULL；“继承模板父级”改由 inherit_parent 表示
ALTER TABLE time_tracker_category ADD COLUMN inherit_parent boolean NOT NULL DEFAULT false;
UPDATE time_tracker_category SET parent_id = NULL, inherit_parent = false
 WHERE template_id IS NULL AND parent_id = 0;
UPDATE time_tracker_category SET parent_id = NULL, inherit_parent = true
 WHERE template_id IS NOT NULL AND parent_id IS NULL;
UPDATE time_tracker_category SET parent_id = NULL, inherit_parent = false
 WHERE template_id IS NOT NULL AND parent_id = 0;
ALTER TABLE time_tracker_category ADD CONSTRAINT ck_time_tracker_category_parent_root CHECK (parent_id IS NULL OR parent_id > 0);
ALTER TABLE time_tracker_category ADD CONSTRAINT ck_time_tracker_category_parent_self CHECK (parent_id <> id);
ALTER TABLE time_tracker_category ADD CONSTRAINT ck_time_tracker_category_inherit CHECK (
    NOT inherit_parent OR (template_id IS NOT NULL AND parent_id IS NULL)
);
COMMENT ON COLUMN time_tracker_category.parent_id IS '上级分类ID；NULL 表示根分类，覆盖记录是否继承模板父级由 inherit_parent 决定';
COMMENT ON COLUMN time_tracker_category.inherit_parent IS '覆盖记录是否继承公共分类的父级；非覆盖记录固定为 false';
COMMENT ON CONSTRAINT ck_time_tracker_category_parent_root ON time_tracker_category IS '根分类使用 NULL，父级ID必须为正数';
COMMENT ON CONSTRAINT ck_time_tracker_category_parent_self ON time_tracker_category IS '分类不能以自身为父级';
COMMENT ON CONSTRAINT ck_time_tracker_category_inherit ON time_tracker_category IS '只有覆盖记录可以继承父级，且继承时不得同时保存本地父级';