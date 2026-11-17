package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.aiolife.record.pojo.entity.CbtiPersonalityEntity;

/**
 * CBTI 人格类型 Mapper 接口
 *
 * @author Ethan
 * @date 2026/04/18
 */
public interface ICbtiPersonalityMapper extends BaseMapper<CbtiPersonalityEntity> {

    /** 图片替换前锁定人格行，串行化同一人格的上传与初始化。 */
    @Select("SELECT id FROM cbti_personality WHERE id=#{id} AND is_deleted=0 FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    Long lockById(@Param("id") long id);
}

