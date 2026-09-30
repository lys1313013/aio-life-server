package top.aiolife.sso.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import top.aiolife.sso.pojo.entity.UserEntity;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/04/04 12:42
 */
public interface UserMapper extends BaseMapper<UserEntity> {
    /** 绑定及首次设密时锁定账号，避免并发覆盖已有凭证。 */
    @org.apache.ibatis.annotations.Select("SELECT * FROM `user` WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    UserEntity selectForAuthUpdate(@org.apache.ibatis.annotations.Param("id") long id);
}
