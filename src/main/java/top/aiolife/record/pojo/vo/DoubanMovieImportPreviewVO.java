package top.aiolife.record.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
public class DoubanMovieImportPreviewVO {

    private int total;

    private int newCount;

    private int duplicateCount;

    private int errorCount;

    private List<DuplicateItem> duplicates = new ArrayList<>();

    private List<ErrorItem> errors = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DuplicateItem {
        private Integer rowNumber;
        private String doubanSubjectId;
        private String title;
        private String existingId;
        private String existingTitle;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorItem {
        private Integer rowNumber;
        private String message;
    }
}
