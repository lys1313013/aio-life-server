package top.aiolife.wardrobe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import top.aiolife.wardrobe.pojo.entity.WardrobeCategoryEntity;

/**
 * 衣柜分类 Mapper
 */
@Mapper
public interface WardrobeCategoryMapper extends BaseMapper<WardrobeCategoryEntity> {

    /** 按主键顺序锁定分类关系，避免并发移动或新增破坏两级结构。 */
    @Select("SELECT id FROM wardrobe_category WHERE is_deleted = 0 ORDER BY id FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<Long> lockHierarchy();
}
