package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.aiolife.record.pojo.entity.CreativeWorkEntity;

@Mapper
public interface CreativeWorkMapper extends BaseMapper<CreativeWorkEntity> {
    @Insert("""
        INSERT INTO creative_work(id, platform, resource_type, external_key, kind, title)
        VALUES (#{id}, 'douban', #{resourceType}, #{key}, #{kind}, #{key})
        ON CONFLICT (platform, resource_type, external_key) WHERE external_key IS NOT NULL
        DO UPDATE SET is_deleted = 0 WHERE creative_work.is_deleted <> 0
        """)
    void register(long id, String resourceType, String key, String kind);

    @Select("""
        SELECT * FROM creative_work WHERE platform='douban' AND resource_type=#{resourceType}
        AND external_key=#{key} AND is_deleted=0
        """)
    CreativeWorkEntity findSubject(String resourceType, String key);

    @Select("""
        <script>
        SELECT * FROM creative_work
        WHERE resource_type=#{resourceType} AND is_deleted=0 AND (platform, external_key) IN
        <foreach collection="sources" item="source" open="(" separator="," close=")">
            (#{source.platform}, #{source.externalKey})
        </foreach>
        </script>
        """)
    List<CreativeWorkEntity> findSources(@Param("resourceType") String resourceType,
                                         @Param("sources") Collection<Source> sources);

    record Source(String platform, String externalKey) {}

    @Select("SELECT * FROM creative_work WHERE id=#{id} AND is_deleted=0 FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    CreativeWorkEntity lock(long id);
}
