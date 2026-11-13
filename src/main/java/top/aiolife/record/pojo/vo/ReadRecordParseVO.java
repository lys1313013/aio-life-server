package top.aiolife.record.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 豆瓣书籍条目解析结果，用于新增表单回填。
 *
 * @author Lys
 * @date 2026/11/13
 */
@Data
public class ReadRecordParseVO {

    @Schema(description = "书名或文章标题")
    private String title;

    @Schema(description = "阅读类型：1=书籍，2=文章或网页", allowableValues = {"1", "2"})
    private Integer type;

    @Schema(description = "作者")
    private String author;

    @Schema(description = "规范化后的豆瓣条目地址")
    private String url;

    @Schema(description = "已入库的封面文件 ID；封面上传失败时为空")
    private String fileId;

    @Schema(description = "封面原始地址；封面已上传时为空")
    private String coverImgUrl;

    @Schema(description = "总页数")
    private Integer totalProgress;
}
