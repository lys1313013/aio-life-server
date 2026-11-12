-- 目的：增加可配置的菜单展示端位掩码，移动端时迹从底栏进入，不重复展示菜单入口。
-- 前置：PostgreSQL 18+；已执行 V20261112_15。
-- 影响：合并 Web／移动端启用状态到一个展示端位掩码并删除两列；保留每端已有配置，时迹取消移动端菜单展示；不修改角色、菜单锁或业务数据。
-- 重跑：Flyway 成功后不重跑；结构重复执行会失败，事务内失败自动回滚。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate，遇错停止。
-- 验证：旧两列不存在；visible_clients 非空且仅含已定义位，除时迹清除移动端位外保留旧两端状态。

ALTER TABLE sys_menu ADD COLUMN visible_clients INTEGER NOT NULL DEFAULT 3;
COMMENT ON COLUMN sys_menu.visible_clients IS '菜单展示端位掩码：1=Web，2=移动端，3=两端，0=均不展示；不改变访问权限';

UPDATE sys_menu
SET visible_clients = web_is_enabled::INTEGER | (mobile_is_enabled::INTEGER << 1);

ALTER TABLE sys_menu DROP COLUMN web_is_enabled;
ALTER TABLE sys_menu DROP COLUMN mobile_is_enabled;

ALTER TABLE sys_menu ADD CONSTRAINT ck_sys_menu_visible_clients CHECK (visible_clients >= 0 AND (visible_clients & ~3) = 0);
COMMENT ON CONSTRAINT ck_sys_menu_visible_clients ON sys_menu IS '展示端只允许组合已定义的 Web 和移动端位';

UPDATE sys_menu
SET visible_clients = visible_clients & ~2, update_time = CURRENT_TIMESTAMP
WHERE path = '/time/time-tracker' AND is_deleted = 0;
