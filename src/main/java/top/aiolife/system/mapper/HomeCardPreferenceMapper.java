package top.aiolife.system.mapper;

import top.aiolife.system.pojo.entity.HomeCardPreferenceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface HomeCardPreferenceMapper extends BaseMapper<HomeCardPreferenceEntity> {
    @Select("SELECT id FROM `user` WHERE id = #{userId} AND is_deleted = 0 FOR UPDATE")
    Long lockUser(@Param("userId") long userId);

    /** Preferences use physical reset so the unique key remains reusable. */
    @Delete("DELETE FROM user_home_card_preference WHERE user_id = #{userId}")
    int reset(@Param("userId") long userId);
}
