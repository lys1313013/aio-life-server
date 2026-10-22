package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.aiolife.record.pojo.entity.DeviceEntity;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/04/04 19:21
 */
public interface IDeviceMapper extends BaseMapper<DeviceEntity> {

    @Select("SELECT * FROM device WHERE id = #{id} AND user_id = #{userId} AND is_deleted = 0 FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    DeviceEntity lockOwned(@Param("userId") long userId, @Param("id") long id);
}
