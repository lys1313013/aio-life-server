-- 目的：明确密码库账号为明文存储，与密码、备注的客户端加密区分。
-- 前置：PostgreSQL 18+；已执行当前初始迁移及其后全部适用增量，password_vault 结构不变。
-- 影响：仅更新 password_vault 的表注释和 username 列注释，不改结构、不改数据、不回填。
-- 重跑：Flyway 成功后不重跑；手工重复执行 COMMENT 结果相同，不产生数据影响。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；失败事务回滚。

COMMENT ON TABLE password_vault IS '密码库表（账号明文；密码及备注由客户端加密，服务端不进行加解密）';
COMMENT ON COLUMN password_vault.username IS '账号明文，用于列表展示与复制；服务端不加密、不解密，客户端直接提交原值';
