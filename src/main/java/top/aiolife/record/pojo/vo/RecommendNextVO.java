package top.aiolife.record.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.aiolife.record.pojo.entity.TimeRecordEntity;

import java.util.List;

/**
 * 推荐下一个时间块响应VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendNextVO {
    /**
     * 推荐的时间块；当天没有剩余分钟时为 null
     */
    private TimeRecordEntity recommend;
    
    /**
     * 当天已有的时间记录列表
     */
    private List<TimeRecordEntity> records;
}
