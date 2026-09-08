package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

import java.time.LocalDate;

/**
 * 豆瓣观影 Excel 中的一行记录。
 */
@Data
public class DoubanMovieImportItemReq {

    private Integer rowNumber;

    private String doubanSubjectId;

    private String title;

    private String type;

    private String director;

    private String url;

    private ProgressStatusEnum status;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate markedDate;

    private Integer rating;

    private String remark;
}
