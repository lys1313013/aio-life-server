package top.aiolife.export.config;

import jakarta.annotation.PostConstruct;

import lombok.Getter;
import lombok.Setter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 导出资源预算。月额度是唯一周期生成额度，不设置日额度。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "aio.life.server.data-export")
public class ExportProperties {
    @Value("${AIO_LIFE_DATA_EXPORT_ENABLED:false}")
    private boolean enabled = false;

    private int monthlyLimit = 5;
    private int batchSize = 500;
    private int maxConcurrent = 2;
    private int maxQueued = 100;
    private int artifactHours = 24;
    private int maxDownloads = 20;
    private int maxMinutes = 15;
    private long maxBytes = 524288000L;
    private long maxTemporaryBytes = 2147483648L;
    private int sheetRows = 100000;
    private int workbookRows = 500000;
    private String bucket = "aio-life-user-exports";
    private String temporaryDirectory = System.getProperty("java.io.tmpdir") + "/aio-life-exports";

    @PostConstruct
    void validate() {
        if (monthlyLimit < 1
                || monthlyLimit > 5
                || batchSize < 1
                || batchSize > 1000
                || maxConcurrent < 1
                || maxConcurrent > 2
                || maxQueued < 1
                || maxQueued > 100
                || sheetRows < 1
                || sheetRows > 1048575
                || workbookRows < sheetRows
                || maxMinutes < 1
                || maxMinutes > 30
                || maxDownloads < 1
                || artifactHours < 1
                || maxBytes < 1024
                || maxTemporaryBytes < maxBytes)
            throw new IllegalArgumentException("Invalid data export limits");
    }
}
