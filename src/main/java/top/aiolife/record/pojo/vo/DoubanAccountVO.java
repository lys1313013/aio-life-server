package top.aiolife.record.pojo.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 豆瓣公开账号信息。
 */
@Data
@Builder
public class DoubanAccountVO {

    private String accountId;

    private String nickname;

    private String homepageUrl;
}
