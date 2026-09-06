-- B站学习状态复用 ProgressStatusEnum，并移除“部分完成”。
-- 执行前停止旧版本写入；盘点结果应只包含 1/2/3/4/5/NULL。

SELECT status, COUNT(*) AS count FROM b_video GROUP BY status ORDER BY status;

ALTER TABLE b_video
    MODIFY COLUMN status varchar(32) DEFAULT 'in_progress'
        COMMENT '学习状态：not_started/in_progress/on_hold/completed';

UPDATE b_video
SET status = COALESCE(CASE status
    WHEN '1' THEN 'not_started'
    WHEN '2' THEN 'in_progress'
    WHEN '3' THEN 'on_hold'
    WHEN '4' THEN 'in_progress'
    WHEN '5' THEN 'completed'
    ELSE status
END, 'in_progress');

ALTER TABLE b_video
    MODIFY COLUMN status varchar(32) NOT NULL DEFAULT 'in_progress'
        COMMENT '学习状态：not_started/in_progress/on_hold/completed',
    ADD CONSTRAINT chk_b_video_status
        CHECK (BINARY status IN ('not_started', 'in_progress', 'on_hold', 'completed'));

SELECT status, COUNT(*) AS count FROM b_video GROUP BY status ORDER BY status;
