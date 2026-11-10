package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import top.aiolife.record.pojo.entity.ExpenseEntity;
import top.aiolife.record.pojo.vo.ExpStaByYearVO;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/10/03 21:50
 */
public interface IExpenseMapper extends BaseMapper<ExpenseEntity> {

    @Select("SELECT * FROM expense WHERE id = #{id} AND user_id = #{userId} AND is_deleted = 0 FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    ExpenseEntity lockOwned(@Param("userId") long userId, @Param("id") long id);

    @Select("""
            SELECT TO_CHAR(exp_time, 'YYYY') AS year ,sum(amt) as amt,exp_type_id AS type_id
            FROM expense
            WHERE user_id = #{userId} and is_deleted = 0  
            GROUP BY TO_CHAR(exp_time, 'YYYY'), exp_type_id
            """)
    List<ExpStaByYearVO> statisticsByYear(long userId);

    @Select("""
            SELECT TO_CHAR(exp_time, 'YYYY') AS year, TO_CHAR(exp_time, 'MM') AS month ,sum(amt) as amt,exp_type_id AS type_id
            FROM expense
            WHERE user_id = #{userId} and is_deleted = 0  
            GROUP BY TO_CHAR(exp_time, 'YYYY'), TO_CHAR(exp_time, 'MM'), exp_type_id
            """)
    List<ExpStaByYearVO> statisticsByMonth(long userId);
}
