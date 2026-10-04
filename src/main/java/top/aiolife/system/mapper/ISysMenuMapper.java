package top.aiolife.system.mapper;

import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import top.aiolife.system.pojo.entity.SysMenuEntity;

/**
 * 系统菜单 Mapper 接口
 *
 * @author Ethan
 * @date 2026/04/19
 */
public interface ISysMenuMapper extends BaseMapper<SysMenuEntity> {
    /** 显式查询避免 MP 注入的 BaseMapper 方法忽略 @Options；禁止复用授权快照。 */
    @Select("SELECT id, parent_id, name, path, icon_color, component, redirect, meta, roles, sort, status, mobile_status FROM sys_menu WHERE is_deleted=0 AND status=1 ORDER BY sort,id")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<SysMenuEntity> selectEnabledForAccessControl();

    /** 菜单锁独立于端展示状态，停用入口不能绕过已有锁。 */
    @Select("SELECT id, parent_id, name, path, icon_color, component, redirect, meta, roles, sort, status, mobile_status FROM sys_menu WHERE is_deleted=0 ORDER BY sort,id")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<SysMenuEntity> selectAllForAccessControl();
}
