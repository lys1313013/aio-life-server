package top.aiolife.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import top.aiolife.system.pojo.entity.ActivityLogEntity;
import top.aiolife.system.pojo.query.ActivityLogQuery;

public interface ActivityLogMapper extends BaseMapper<ActivityLogEntity> {
    Page<ActivityLogEntity> selectOperationPage(Page<ActivityLogEntity> page, @Param("query") ActivityLogQuery query);
    Long operationMaxId(@Param("query") ActivityLogQuery query);
    List<ActivityLogEntity> exportOperations(@Param("query") ActivityLogQuery query,
            @Param("maxId") Long maxId, @Param("cursor") Long cursor);

}
