package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import org.springframework.stereotype.Service;
import top.aiolife.core.query.QueryOrder;
import top.aiolife.record.mapper.IHonorCategoryMapper;
import top.aiolife.record.pojo.entity.HonorCategoryEntity;
import top.aiolife.record.service.IHonorCategoryService;

/**
 * 荣誉分类服务实现
 *
 * @author Lys
 * @date 2026/04/11
 */
@Service
public class HonorCategoryServiceImpl extends ServiceImpl<IHonorCategoryMapper, HonorCategoryEntity>
        implements IHonorCategoryService {

    @Override
    public List<HonorCategoryEntity> listForUser(long userId) {
        LambdaQueryWrapper<HonorCategoryEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper
                .eq(HonorCategoryEntity::getUserId, userId)
                .or()
                .isNull(HonorCategoryEntity::getUserId)
        );
        QueryOrder.ascNullsFirst(queryWrapper, HonorCategoryEntity::getSortOrder);
        return this.list(queryWrapper);
    }
}
