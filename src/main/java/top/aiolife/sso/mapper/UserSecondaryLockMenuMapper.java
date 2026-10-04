package top.aiolife.sso.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.aiolife.sso.pojo.entity.UserSecondaryLockMenuEntity;

/**
 * 用户二级锁菜单 Mapper
 *
 * @author Lys
 * @date 2026/07/25
 */
@Mapper
public interface UserSecondaryLockMenuMapper extends BaseMapper<UserSecondaryLockMenuEntity> {
    /** 显式查询避免 MP 注入的 BaseMapper 方法忽略 @Options；禁止复用授权快照。 */
    @Select("SELECT id, user_id, menu_id FROM user_secondary_lock_menu WHERE user_id=#{userId} AND is_deleted=0")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<UserSecondaryLockMenuEntity> selectForAccessControl(@Param("userId") long userId);
}
