package top.aiolife.export.mapper;

import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.system.pojo.entity.SysMenuEntity;

import java.util.List;

/** 权限不使用业务快照及 MyBatis 查询缓存，撤权实时生效。 */
public interface ExportSecurityMapper {
    @Select("SELECT * FROM `user` WHERE id = #{userId} AND is_deleted = 0")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    UserEntity user(@Param("userId") long userId);

    @Select("SELECT * FROM sys_menu WHERE is_deleted = 0")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<SysMenuEntity> menus();

    @Select(
            "SELECT menu_id FROM user_secondary_lock_menu WHERE user_id = #{userId} AND is_deleted"
                    + " = 0")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<Long> lockedMenus(@Param("userId") long userId);
}
