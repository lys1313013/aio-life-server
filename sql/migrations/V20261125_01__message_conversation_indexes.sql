-- 支持按当前用户的会话扫描及按对方和消息 ID 游标读取。
CREATE INDEX idx_message_active_sender_receiver_id
    ON message (sender_id, receiver_id, id DESC) WHERE is_deleted = 0;

CREATE INDEX idx_message_active_receiver_sender_id
    ON message (receiver_id, sender_id, id DESC) WHERE is_deleted = 0;
