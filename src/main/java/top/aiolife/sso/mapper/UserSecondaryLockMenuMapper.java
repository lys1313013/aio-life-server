package top.aiolife.sso.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.aiolife.sso.pojo.entity.UserSecondaryLockMenuEntity;

/**
 * 用户二级锁菜单 Mapper
 *
 * @author Lys
 * @date 2026/07/25
 */
@Mapper
public interface UserSecondaryLockMenuMapper extends BaseMapper<UserSecondaryLockMenuEntity> {
}
