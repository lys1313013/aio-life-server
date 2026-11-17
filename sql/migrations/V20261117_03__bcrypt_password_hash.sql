-- 目的：登录密码与二级密码统一使用 bcrypt，删除独立盐列。
-- 前置：PostgreSQL 18+，已执行至 V20261117_02；sys_user 含两个独立盐列。
-- 影响：仅删除 password_salt、secondary_password_salt 并更新哈希列注释；其余列及数据不变。
-- 不回填或转换旧密码；应用不再接受旧 MD5 哈希，已有账号需重新设置密码。
-- 重跑：由 Flyway 账本保证成功版本不重跑；DDL 在事务内执行，失败回滚后排查并重新 migrate。
-- 执行：停写后通过独立 Flyway 入口 migrate、validate，再启动配套 bcrypt 版本应用。
-- 验证：PasswordHashMigrationIT 验证增量、无关数据保留及重复 migrate；PostgresqlMigrationIT 验证空库初始化。

ALTER TABLE sys_user DROP COLUMN password_salt;
ALTER TABLE sys_user DROP COLUMN secondary_password_salt;

COMMENT ON COLUMN sys_user.password IS '登录密码bcrypt哈希，包含随机盐与计算成本；NULL表示未设置';
COMMENT ON COLUMN sys_user.secondary_password IS '二级密码bcrypt哈希，包含随机盐与计算成本；NULL表示未设置';
