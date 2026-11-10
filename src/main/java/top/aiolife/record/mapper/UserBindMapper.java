package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Options;
import java.time.LocalDateTime;
import top.aiolife.record.pojo.entity.UserBindEntity;

@Mapper
public interface UserBindMapper extends BaseMapper<UserBindEntity> {
    /** 两个微信读书配置入口按用户串行写入，避免并发创建重复绑定。 */
    @Select("SELECT id FROM \"user\" WHERE id = #{userId} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Long lockWereadUser(@Param("userId") Long userId);

    /** 原子清除同步时间和凭证，保留其他元数据；随后由服务逻辑删除绑定。 */
    @Update("""
        UPDATE user_bind SET access_token=NULL,
          meta_fields=COALESCE(meta_fields, '{}'::jsonb) - 'lastSyncTime',
          update_time=#{now}, update_user=#{userId}
        WHERE user_id=#{userId} AND platform='weread' AND is_deleted=0
        """)
    int clearWereadCredentials(@Param("userId") long userId, @Param("now") LocalDateTime now);

    /** 用连接版本防止旧请求覆盖换绑后的状态；只修改同步时间这个 JSON 键。 */
    @Update("""
        UPDATE user_bind SET
          meta_fields=jsonb_set(COALESCE(meta_fields, '{}'::jsonb), '{lastSyncTime}',
            to_jsonb(CAST(#{syncTime} AS text)), true),
          update_time=#{now}, update_user=#{userId}
        WHERE id=#{id} AND user_id=#{userId} AND platform='weread' AND is_deleted=0
          AND meta_fields ->> 'connectionVersion' = #{version}
        """)
    int recordWereadSync(@Param("id") long id, @Param("userId") long userId,
                         @Param("version") String version, @Param("syncTime") String syncTime,
                         @Param("now") LocalDateTime now);
}
