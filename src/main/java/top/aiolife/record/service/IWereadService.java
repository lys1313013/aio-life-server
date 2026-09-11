package top.aiolife.record.service;

import com.fasterxml.jackson.databind.JsonNode;
import top.aiolife.record.pojo.vo.WereadConnectionVO;

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
}
