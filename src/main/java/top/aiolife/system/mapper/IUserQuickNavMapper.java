package top.aiolife.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.aiolife.system.pojo.entity.UserQuickNavEntity;

/**
 * 用户快捷导航 Mapper 接口
 *
 * @author Ethan
 * @date 2026/06/05
 */
@Mapper
public interface IUserQuickNavMapper extends BaseMapper<UserQuickNavEntity> {

    /** 显式 DELETE，不能使用 BaseMapper 的逻辑删除方法。 */
    @Delete("DELETE FROM user_quick_nav WHERE user_id = #{userId}")
    int physicalDeleteByUserId(@Param("userId") long userId);
}
