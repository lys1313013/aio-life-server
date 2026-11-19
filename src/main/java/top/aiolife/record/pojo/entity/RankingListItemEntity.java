package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

@Getter
@Setter
@TableName("ranking_list_item")
public class RankingListItemEntity extends BaseEntity {
    private Long rankingListId;
    private Long creativeWorkId;
    private Integer rank;
    private Integer sortOrder;
}
