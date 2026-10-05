package top.aiolife.record.service;

import com.fasterxml.jackson.databind.JsonNode;
import top.aiolife.record.pojo.vo.WereadConnectionVO;
import top.aiolife.record.pojo.vo.WereadBookLinkVO;
import top.aiolife.record.pojo.vo.WereadRecentVO;

public interface IWereadService {
    WereadConnectionVO connection();
    WereadConnectionVO connect(String apiKey);
    void disconnect();
    default JsonNode sync(String mode) { return sync(mode, 0); }
    JsonNode sync(String mode, long baseTime);
    default JsonNode stats(String mode) { return stats(mode, 0); }
    JsonNode stats(String mode, long baseTime);
    JsonNode notes(String bookId);
    JsonNode progress(String bookId);
    WereadBookLinkVO bookLink(String bookId);
    default WereadRecentVO recent() { return recent(null, 6); }
    WereadRecentVO recent(String cursor, int size);
}
