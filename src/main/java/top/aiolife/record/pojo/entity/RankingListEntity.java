package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

@Getter
@Setter
@TableName("ranking_list")
public class RankingListEntity extends BaseEntity {
    private String name;
    private String publisherCode;
    private String listKey;
    private String editionKey;
    private String sourceUrl;
    private LocalDateTime lastSyncTime;
}
