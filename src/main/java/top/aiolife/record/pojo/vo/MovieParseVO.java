package top.aiolife.record.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 豆瓣影视条目解析结果，用于新增表单回填。
 *
 * @author Lys
 * @date 2026/11/13
 */
@Data
public class MovieParseVO {

    @Schema(description = "影片或剧集标题")
    private String title;

    @Schema(description = "类型：1=电影，2=电视剧", allowableValues = {"1", "2"})
    private Integer type;

    @Schema(description = "导演")
    private String director;

    @Schema(description = "规范化后的豆瓣条目地址")
    private String url;

    @Schema(description = "已入库的封面文件 ID；封面上传失败时为空")
    private String fileId;

    @Schema(description = "封面原始地址；封面已上传时为空")
    private String coverImgUrl;

    @Schema(description = "总集数或总时长")
    private Integer totalProgress;
}
