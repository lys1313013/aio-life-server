-- 目的：观影、阅读记录按豆瓣链接的条目 ID 查共享作品。
-- 前置：PostgreSQL 18+，V20261118_14 已执行。先切换只读链接的新应用。
-- 影响：移除两张个人记录表的冗余作品 ID 与其依赖索引。
-- 重跑：Flyway 事务执行。若有无法由链接重建的关联则整次回滚，按提示核对后再运行。
-- 验证：个人记录及 URL 保留，按平台、条目类型和外部 ID 匹配原作品。
SET LOCAL lock_timeout = '10s';
LOCK TABLE movie, read_record IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM (
            SELECT 'movie' AS media, url, creative_work_id FROM movie WHERE creative_work_id IS NOT NULL
            UNION ALL
            SELECT 'book', url, creative_work_id FROM read_record WHERE creative_work_id IS NOT NULL
        ) r
        WHERE NOT EXISTS (
            SELECT 1 FROM creative_work w
            WHERE w.id = r.creative_work_id AND w.platform = 'douban'
              AND w.resource_type = r.media || '_subject'
              AND w.external_key = substring(trim(r.url) FROM
                  ('(?i)^https?://' || r.media || '[.]douban[.]com/subject/([0-9]+)/?([?#].*)?$'))
        )
    ) THEN
        RAISE EXCEPTION '存在无法从豆瓣链接还原的作品关联，请先核对记录 URL 和外部条目 ID';
    END IF;
END $$;

ALTER TABLE movie DROP COLUMN creative_work_id;
ALTER TABLE read_record DROP COLUMN creative_work_id;
