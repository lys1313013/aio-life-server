package top.aiolife.system.mapper;

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
    @org.apache.ibatis.annotations.Select("SELECT id, parent_id, name, path, icon_color, component, redirect, meta, roles, sort, status FROM sys_menu WHERE is_deleted=0 AND status=1 ORDER BY sort,id")
    @org.apache.ibatis.annotations.Options(useCache = false, flushCache = org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    java.util.List<SysMenuEntity> selectEnabledForAccessControl();
}
