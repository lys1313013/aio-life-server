package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public record WereadConnectionVO(boolean connected,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime lastSyncTime) {}
