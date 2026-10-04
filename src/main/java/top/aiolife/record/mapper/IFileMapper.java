package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.aiolife.record.pojo.entity.FileEntity;

@Mapper
public interface IFileMapper extends BaseMapper<FileEntity> {
    @Select("SELECT * FROM file WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    FileEntity selectForAvatarBinding(@Param("id") String id);
}
