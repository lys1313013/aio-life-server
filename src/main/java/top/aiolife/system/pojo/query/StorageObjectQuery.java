package top.aiolife.system.pojo.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 对象存储目录查询；prefix 是原始对象名前缀，不做路径标准化。 */
@Data
public class StorageObjectQuery {
    @Size(max = 1024)
    private String prefix = "";
    @Size(max = 4096)
    private String cursor;
    @Min(1)
    @Max(100)
    private int pageSize = 24;
}
