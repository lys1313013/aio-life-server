-- 阅读、观影、目标共用语义化进度状态。
-- 执行前应先停止旧版本服务写入，并检查下方三个盘点查询只返回 0/1/2/3/NULL。

SELECT status, COUNT(*) AS count FROM goal GROUP BY status ORDER BY status;
SELECT status, COUNT(*) AS count FROM read_record GROUP BY status ORDER BY status;
SELECT status, COUNT(*) AS count FROM movie GROUP BY status ORDER BY status;

ALTER TABLE goal
    MODIFY COLUMN status varchar(32) DEFAULT 'not_started'
        COMMENT '目标状态：not_started/in_progress/completed/on_hold';

UPDATE goal
SET status = CASE status
    WHEN '0' THEN 'not_started'
    WHEN '1' THEN 'in_progress'
    WHEN '2' THEN 'completed'
    WHEN '3' THEN 'on_hold'
    ELSE status
END;

ALTER TABLE goal
    ADD CONSTRAINT chk_goal_status
        CHECK (status IS NULL OR BINARY status IN ('not_started', 'in_progress', 'completed', 'on_hold'));

ALTER TABLE read_record
    MODIFY COLUMN status varchar(32) DEFAULT 'not_started'
        COMMENT '状态：not_started/in_progress/completed/on_hold';

UPDATE read_record
SET status = CASE status
    WHEN '0' THEN 'not_started'
    WHEN '1' THEN 'in_progress'
    WHEN '2' THEN 'completed'
    WHEN '3' THEN 'on_hold'
    ELSE status
END;

ALTER TABLE read_record
    ADD CONSTRAINT chk_read_record_status
        CHECK (status IS NULL OR BINARY status IN ('not_started', 'in_progress', 'completed', 'on_hold'));

ALTER TABLE movie
    MODIFY COLUMN status varchar(32) DEFAULT 'not_started'
        COMMENT '状态：not_started/in_progress/completed/on_hold';

UPDATE movie
SET status = CASE status
    WHEN '0' THEN 'not_started'
    WHEN '1' THEN 'in_progress'
    WHEN '2' THEN 'completed'
    WHEN '3' THEN 'on_hold'
    ELSE status
END;

ALTER TABLE movie
    ADD CONSTRAINT chk_movie_status
        CHECK (status IS NULL OR BINARY status IN ('not_started', 'in_progress', 'completed', 'on_hold'));

SELECT status, COUNT(*) AS count FROM goal GROUP BY status ORDER BY status;
SELECT status, COUNT(*) AS count FROM read_record GROUP BY status ORDER BY status;
SELECT status, COUNT(*) AS count FROM movie GROUP BY status ORDER BY status;
