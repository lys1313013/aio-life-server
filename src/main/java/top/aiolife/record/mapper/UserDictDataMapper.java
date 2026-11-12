package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.aiolife.record.pojo.entity.UserDictDataEntity;

/**
 * 用户字典数据 Mapper 接口
 *
 * @author Lys
 */
@Mapper
public interface UserDictDataMapper extends BaseMapper<UserDictDataEntity> {

    /**
     * 锁定公共字典行，串行化同一公共项的用户覆盖写入。
     */
    @Select("SELECT id FROM user_dict_data WHERE id = #{templateId} AND user_id = 0 AND is_deleted = 0 FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Long lockTemplate(@Param("templateId") long templateId);
}
