package top.aiolife.wardrobe.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.wardrobe.mapper.WardrobeCategoryMapper;
import top.aiolife.wardrobe.pojo.entity.WardrobeCategoryEntity;
import top.aiolife.wardrobe.pojo.req.CategoryReq;
import top.aiolife.wardrobe.pojo.vo.CategoryVO;
import top.aiolife.wardrobe.service.IWardrobeCategoryService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 衣柜分类 Service 实现
 */
@Slf4j
@Service
@AllArgsConstructor
public class WardrobeCategoryServiceImpl extends ServiceImpl<WardrobeCategoryMapper, WardrobeCategoryEntity> implements IWardrobeCategoryService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveCategory(CategoryReq req) {
        WardrobeCategoryEntity entity = reqToEntity(req);
        Long userId = StpUtil.getLoginIdAsLong();
        entity.setUserId(userId);
        entity.setCategoryType(1); // 用户自定义分类
        entity.fillCreateCommonField(userId);
        this.save(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(CategoryReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        WardrobeCategoryEntity existing = this.getById(req.getId());
        if (existing == null) {
            throw new RuntimeException("分类不存在");
        }
        if (existing.getCategoryType() != null && existing.getCategoryType() == 0) {
            throw new RuntimeException("系统预设分类不可修改");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权修改此分类");
        }
        WardrobeCategoryEntity entity = reqToEntity(req);
        entity.setId(req.getId());
        entity.fillUpdateCommonField(userId);
        this.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeCategory(Long id, Long userId) {
        // 只能删除用户自定义的分类
        LambdaQueryWrapper<WardrobeCategoryEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WardrobeCategoryEntity::getId, id);
        queryWrapper.eq(WardrobeCategoryEntity::getUserId, userId);
        queryWrapper.eq(WardrobeCategoryEntity::getCategoryType, 1); // 仅用户自定义分类
        this.remove(queryWrapper);
    }

    @Override
    public List<CategoryVO> listCategories(Long userId) {
        LambdaQueryWrapper<WardrobeCategoryEntity> queryWrapper = new LambdaQueryWrapper<>();
        // 获取系统预设分类和用户自定义分类
        queryWrapper.and(w -> w
                .eq(WardrobeCategoryEntity::getCategoryType, 0)
                .or()
                .eq(WardrobeCategoryEntity::getCategoryType, 1)
                .eq(WardrobeCategoryEntity::getUserId, userId)
        );
        queryWrapper.orderByAsc(WardrobeCategoryEntity::getSortOrder);

        List<WardrobeCategoryEntity> list = this.list(queryWrapper);
        return buildTree(list);
    }

    private WardrobeCategoryEntity reqToEntity(CategoryReq req) {
        WardrobeCategoryEntity entity = new WardrobeCategoryEntity();
        entity.setName(req.getName());
        entity.setIcon(req.getIcon());
        entity.setParentId(req.getParentId());
        entity.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        return entity;
    }

    private List<CategoryVO> buildTree(List<WardrobeCategoryEntity> list) {
        Map<Long, List<WardrobeCategoryEntity>> parentMap = list.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getParentId() == null ? 0L : e.getParentId()
                ));

        return parentMap.getOrDefault(0L, List.of()).stream()
                .map(parent -> {
                    CategoryVO vo = toVO(parent);
                    List<CategoryVO> children = parentMap.getOrDefault(parent.getId(), List.of()).stream()
                            .map(this::toVO)
                            .collect(Collectors.toList());
                    vo.setChildren(children);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private CategoryVO toVO(WardrobeCategoryEntity entity) {
        CategoryVO vo = new CategoryVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setIcon(entity.getIcon());
        vo.setParentId(entity.getParentId());
        vo.setSortOrder(entity.getSortOrder());
        vo.setCategoryType(entity.getCategoryType());
        return vo;
    }
}
