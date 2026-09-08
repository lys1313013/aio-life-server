package top.aiolife.record.pojo.req;

import lombok.Data;

import java.util.List;

/**
 * douban-exporter 生成文件的导入请求。
 */
@Data
public class DoubanMovieImportReq {

    private String format;

    private Integer version;

    private String source;

    private String doubanUserId;

    /** skip（默认）或 overwrite。 */
    private String duplicatePolicy;

    private List<DoubanMovieImportItemReq> records;
}
