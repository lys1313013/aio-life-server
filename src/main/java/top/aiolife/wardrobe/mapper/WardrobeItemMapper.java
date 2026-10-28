package top.aiolife.wardrobe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.aiolife.wardrobe.pojo.dto.WardrobeCategoryCount;
import top.aiolife.wardrobe.pojo.dto.WardrobeStatsAggregate;
import top.aiolife.wardrobe.pojo.entity.WardrobeItemEntity;

/**
 * 衣柜衣物 Mapper
 */
@Mapper
public interface WardrobeItemMapper extends BaseMapper<WardrobeItemEntity> {

    /** 保留多季节包含语义；空价格仍计入衣物总数，均价不能使用 AVG(price)。 */
    @Select("""
            SELECT COUNT(*) AS totalCount, COALESCE(SUM(price), 0) AS totalValue,
                   COALESCE(SUM(CASE WHEN season LIKE '%春%' THEN 1 ELSE 0 END), 0) AS springCount,
                   COALESCE(SUM(CASE WHEN season LIKE '%夏%' THEN 1 ELSE 0 END), 0) AS summerCount,
                   COALESCE(SUM(CASE WHEN season LIKE '%秋%' THEN 1 ELSE 0 END), 0) AS autumnCount,
                   COALESCE(SUM(CASE WHEN season LIKE '%冬%' THEN 1 ELSE 0 END), 0) AS winterCount
            FROM wardrobe_item
            WHERE user_id = #{userId} AND is_deleted = 0
            """)
    WardrobeStatsAggregate aggregateStats(@Param("userId") long userId);

    /** 按 ID 聚合后再解析可见名称，避免数据库排序规则合并大小写或尾空格不同的名称。 */
    @Select("""
            SELECT category.name AS categoryName, totals.itemCount
            FROM (
                SELECT category_id, COUNT(*) AS itemCount
                FROM wardrobe_item
                WHERE user_id = #{userId} AND is_deleted = 0
                GROUP BY category_id
            ) totals
            LEFT JOIN wardrobe_category category ON category.id = totals.category_id
                AND category.is_deleted = 0
                AND (category.category_type = 0
                     OR (category.category_type = 1 AND category.user_id = #{userId}))
            """)
    List<WardrobeCategoryCount> aggregateCategoryCounts(@Param("userId") long userId);

    /** 当前读：用户行锁后读取订单物品，避免并发提交看到旧快照。 */
    @Select("SELECT * FROM wardrobe_item WHERE user_id = #{userId} AND source_platform = #{platform}"
            + " AND source_order_id = #{orderId} AND is_deleted = 0 FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<WardrobeItemEntity> lockCaptureOrder(@Param("userId") long userId,
            @Param("platform") String platform, @Param("orderId") String orderId);
}
