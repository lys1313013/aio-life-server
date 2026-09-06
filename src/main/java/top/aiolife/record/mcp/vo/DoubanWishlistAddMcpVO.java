package top.aiolife.record.mcp.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DoubanWishlistAddMcpVO {

    private boolean created;

    /** movie 或 book。 */
    private String mediaType;

    private Long id;

    private String title;

    private String status;

    private String fileId;

    private String url;
}
