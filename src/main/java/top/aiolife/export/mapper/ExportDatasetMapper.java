package top.aiolife.export.mapper;

import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;
import java.util.Map;

public interface ExportDatasetMapper {
    @SelectProvider(type = ExportDatasetSql.class, method = "select")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<Map<String, Object>> read(
            @Param("definition") SqlDatasetDefinition definition,
            @Param("ownerId") long ownerId,
            @Param("cursor") String cursor,
            @Param("limit") int limit);
}
