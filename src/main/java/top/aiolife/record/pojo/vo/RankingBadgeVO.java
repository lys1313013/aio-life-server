package top.aiolife.record.pojo.vo;

import lombok.Data;

@Data
public class RankingBadgeVO {
    private Long creativeWorkId;
    private Long rankingListId;
    private String name;
    private Integer rank;
}
