package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.aiolife.record.pojo.entity.UserBindEntity;

@Mapper
public interface UserBindMapper extends BaseMapper<UserBindEntity> {
    /** 两个微信读书配置入口按用户串行写入，避免并发创建重复绑定。 */
    @Select("SELECT id FROM `user` WHERE id = #{userId} FOR UPDATE")
    Long lockWereadUser(@Param("userId") Long userId);
}
