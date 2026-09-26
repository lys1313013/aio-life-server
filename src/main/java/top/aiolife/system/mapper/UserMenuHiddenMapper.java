package top.aiolife.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.aiolife.system.pojo.entity.UserMenuHiddenEntity;

import java.util.List;

@Mapper
public interface UserMenuHiddenMapper extends BaseMapper<UserMenuHiddenEntity> {
    @Select("SELECT menu_id FROM user_menu_hidden WHERE user_id = #{userId} AND is_deleted = 0 ORDER BY menu_id")
    List<Long> selectHiddenMenuIds(@Param("userId") long userId);

    /** 同一用户的保存和恢复默认串行执行，包括首次尚无隐藏记录的情况。 */
    @Select("SELECT id FROM `user` WHERE id = #{userId} AND is_deleted = 0 FOR UPDATE")
    Long lockUser(@Param("userId") long userId);

    /** 显式 DELETE，不能使用 BaseMapper 的逻辑删除方法。 */
    @Delete("DELETE FROM user_menu_hidden WHERE user_id = #{userId}")
    int physicalDeleteByUserId(@Param("userId") long userId);
}
