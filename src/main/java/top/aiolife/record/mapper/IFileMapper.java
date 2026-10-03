package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.aiolife.record.pojo.entity.FileEntity;

@Mapper
public interface IFileMapper extends BaseMapper<FileEntity> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM file WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    @org.apache.ibatis.annotations.Options(useCache = false, flushCache = org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    FileEntity selectForAvatarBinding(@org.apache.ibatis.annotations.Param("id") String id);
}
