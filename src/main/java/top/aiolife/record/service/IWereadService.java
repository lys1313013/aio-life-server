package top.aiolife.record.service;

import com.fasterxml.jackson.databind.JsonNode;
import top.aiolife.record.pojo.vo.WereadConnectionVO;
import top.aiolife.record.pojo.vo.WereadBookLinkVO;
import top.aiolife.record.pojo.vo.WereadRecentVO;

public interface IWereadService {
    WereadConnectionVO connection(long userId);
    WereadConnectionVO connect(long userId, String apiKey);
    void disconnect(long userId);
    JsonNode sync(long userId, String mode, long baseTime);
    JsonNode stats(long userId, String mode, long baseTime);

    /** 首页卡片使用：按当前用户连接读取指定周期的统计，未连接返回 null。 */
    JsonNode statsForUser(long userId, String mode, long baseTime);
    JsonNode notes(long userId, String bookId);
    JsonNode progress(long userId, String bookId);
    WereadBookLinkVO bookLink(long userId, String bookId);
    WereadRecentVO recent(long userId, String cursor, int size);
}
