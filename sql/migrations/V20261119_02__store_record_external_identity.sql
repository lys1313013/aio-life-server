-- 目的：为观影和阅读保存外部系统编码与条目 ID，供榜单与来源去重查询。
-- 前置：PostgreSQL 18+，V20261119_01 已执行。
-- 影响：两张表新增存储生成列及用户来源索引。存量值从 URL 自动生成，原字段值保留。
-- 重跑：由 Flyway 在同一事务执行，执行失败可重试。
-- 验证：支持的豆瓣链接生成 douban 和完整条目 ID，其他链接两列均为空。
SET LOCAL lock_timeout = '10s';

CREATE FUNCTION record_douban_subject_id(record_url text, subject_domain text)
RETURNS text
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
AS $$
    SELECT substring(btrim(record_url) FROM
        ('(?i)^https?://' || subject_domain || '[.]douban[.]com/subject/([0-9]{1,255})/?(?:[?#].*)?$'))
$$;

COMMENT ON FUNCTION record_douban_subject_id(text, text)
    IS '从观影或阅读的豆瓣条目 URL 提取对应类型的外部条目 ID';

ALTER TABLE movie
    ADD COLUMN platform varchar(32) GENERATED ALWAYS AS (
        CASE WHEN record_douban_subject_id(url, 'movie') IS NOT NULL THEN 'douban' END
    ) STORED,
    ADD COLUMN external_key varchar(255) GENERATED ALWAYS AS (record_douban_subject_id(url, 'movie')) STORED;
COMMENT ON COLUMN movie.platform IS '外部系统编码，由观影链接生成，例如 douban';
COMMENT ON COLUMN movie.external_key IS '外部系统条目 ID，由观影链接生成';
CREATE INDEX idx_movie_user_external ON movie (user_id, platform, external_key)
    WHERE is_deleted = 0 AND external_key IS NOT NULL;

ALTER TABLE read_record
    ADD COLUMN platform varchar(32) GENERATED ALWAYS AS (
        CASE WHEN record_douban_subject_id(url, 'book') IS NOT NULL THEN 'douban' END
    ) STORED,
    ADD COLUMN external_key varchar(255) GENERATED ALWAYS AS (record_douban_subject_id(url, 'book')) STORED;
COMMENT ON COLUMN read_record.platform IS '外部系统编码，由阅读链接生成，例如 douban';
COMMENT ON COLUMN read_record.external_key IS '外部系统条目 ID，由阅读链接生成';
CREATE INDEX idx_read_record_user_external ON read_record (user_id, platform, external_key)
    WHERE is_deleted = 0 AND external_key IS NOT NULL;
