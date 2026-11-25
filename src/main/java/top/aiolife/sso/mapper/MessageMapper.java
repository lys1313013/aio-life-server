package top.aiolife.sso.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import top.aiolife.sso.pojo.entity.MessageEntity;
import top.aiolife.sso.pojo.vo.MessageConversationVO;

@Mapper
public interface MessageMapper extends BaseMapper<MessageEntity> {
    @Select("""
        WITH visible AS (
            SELECT m.id, m.create_time, m.is_read, m.receiver_id,
                   CASE WHEN m.type = 0 THEN 'system'
                        WHEN m.sender_id = #{userId} THEN CAST(m.receiver_id AS varchar)
                        ELSE CAST(m.sender_id AS varchar) END AS peer
            FROM message m
            WHERE m.is_deleted = 0 AND (m.sender_id = #{userId} OR m.receiver_id = #{userId})
        ), ranked AS (
            SELECT id, create_time, peer,
                   ROW_NUMBER() OVER (PARTITION BY peer ORDER BY id DESC) AS row_num,
                   SUM(CASE WHEN receiver_id = #{userId} AND is_read = 0 THEN 1 ELSE 0 END)
                       OVER (PARTITION BY peer) AS unread_count
            FROM visible WHERE peer IS NOT NULL
        ), page AS (
            SELECT * FROM ranked WHERE row_num = 1
            ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}
        )
        SELECT p.peer AS user_id, u.nickname AS username, LEFT(m.content, 180) AS last_message,
               p.create_time AS time, p.unread_count, o.content_hash AS avatar_hash, o.content_type AS avatar_content_type
        FROM page p JOIN message m ON m.id = p.id
        LEFT JOIN sys_user u ON u.id = CAST(NULLIF(p.peer, 'system') AS bigint) AND u.is_deleted = 0
        LEFT JOIN attachment a ON a.biz_type = 'avatar' AND a.biz_id = u.id AND a.user_id = u.id
            AND a.is_deleted = 0 AND a.is_public = 1
        LEFT JOIN storage_object o ON o.id = a.storage_object_id AND o.is_deleted = 0
        ORDER BY p.id DESC
        """)
    List<MessageConversationVO> conversations(long userId, int limit, long offset);
}
