package top.aiolife.export.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import top.aiolife.export.pojo.entity.ExportCoordinatorEntity;

public interface ExportCoordinatorMapper extends BaseMapper<ExportCoordinatorEntity> {
    @Select("SELECT id FROM data_export_coordinator WHERE id = 1 AND is_deleted = 0 FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Long lock();
}
