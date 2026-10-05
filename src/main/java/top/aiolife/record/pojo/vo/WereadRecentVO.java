package top.aiolife.record.pojo.vo;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

/** 首页只返回最近阅读的电子书，不同步笔记或听书。 */
public record WereadRecentVO(boolean connected, List<Book> books, String nextCursor) {
    public record Book(String bookId, String title, String author, String cover,
                       Long readUpdateTime, Integer progress, @JsonInclude(JsonInclude.Include.NON_NULL) String deepLink) {}
}
