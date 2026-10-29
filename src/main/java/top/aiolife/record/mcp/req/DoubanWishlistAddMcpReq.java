package top.aiolife.record.mcp.req;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

@Data
public class DoubanWishlistAddMcpReq {

    @Description("豆瓣电影或书籍条目地址，例如 https://movie.douban.com/subject/36354085/")
    @NotBlank
    private String url;
}
