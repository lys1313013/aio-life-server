-- 目的：API Key 只保存 SHA-256 摘要，不再明文落库。
-- 前置：PostgreSQL 18+，已执行至 V20261117_04；api_key 含明文 api_key 列及唯一索引。
-- 影响：清除旧 Key 和调用日志，删除明文列与唯一索引，新增 api_key_hash、key_prefix。
-- 不回填旧 Key：明文无法在 SQL 内计算摘要，系统未上线，旧 Key 直接作废并需重新生成。
-- 重跑：由 Flyway 账本保证成功版本不重跑；DDL 在事务内执行，失败回滚后排查并重新 migrate。
-- 执行：停写后通过独立 Flyway 入口 migrate、validate，再启动配套版本应用。
-- 验证：ApiKeyHashMigrationIT 验证增量、旧值清除及重复 migrate；PostgresqlMigrationIT 验证空库初始化。

DELETE FROM api_key_log;
DELETE FROM api_key;

DROP INDEX uk_api_key_api_key;

ALTER TABLE api_key DROP COLUMN api_key;
ALTER TABLE api_key ADD COLUMN api_key_hash char(64) COLLATE "C" NOT NULL;
ALTER TABLE api_key ADD COLUMN key_prefix varchar(16) COLLATE "C" NOT NULL;

COMMENT ON COLUMN api_key.api_key_hash IS 'API Key的SHA-256摘要，不保存原文';
COMMENT ON COLUMN api_key.key_prefix IS 'API Key前12位，仅用于列表识别';

CREATE UNIQUE INDEX uk_api_key_hash ON api_key (api_key_hash) WHERE is_deleted = 0;
COMMENT ON INDEX uk_api_key_hash IS '有效API Key摘要唯一，已删除Key可重新生成';
