package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import top.aiolife.record.pojo.entity.IncomeEntity;
import top.aiolife.record.pojo.vo.IncStaByYearVO;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/09/14 21:09
 */
public interface IIncomeMapper extends BaseMapper<IncomeEntity> {

    @Select("""
            SELECT TO_CHAR(inc_date, 'YYYY') AS year ,sum(amt) as amt,inc_type_id as type_id
            FROM income where user_id = #{userId} and is_deleted = 0  
            GROUP BY TO_CHAR(inc_date, 'YYYY'), inc_type_id
            """)
    List<IncStaByYearVO> statisticsByYear(long userId);
    
    @Select("""
            SELECT TO_CHAR(inc_date, 'YYYY') AS year, TO_CHAR(inc_date, 'MM') AS month ,sum(amt) as amt,inc_type_id as type_id
            FROM income where user_id = #{userId} and is_deleted = 0  
            GROUP BY TO_CHAR(inc_date, 'YYYY'), TO_CHAR(inc_date, 'MM'), inc_type_id
            """)
    List<IncStaByYearVO> statisticsByMonth(long userId);
}