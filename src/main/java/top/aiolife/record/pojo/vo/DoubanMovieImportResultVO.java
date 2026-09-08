package top.aiolife.record.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DoubanMovieImportResultVO {

    private int createdCount;

    private int updatedCount;

    private int skippedCount;
}
