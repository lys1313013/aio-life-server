package top.aiolife.wardrobe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import top.aiolife.core.query.QueryOrder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.wardrobe.mapper.WardrobeCategoryMapper;
import top.aiolife.wardrobe.mapper.WardrobeItemMapper;
import top.aiolife.wardrobe.pojo.entity.WardrobeCategoryEntity;
import top.aiolife.wardrobe.pojo.entity.WardrobeItemEntity;
import top.aiolife.wardrobe.pojo.req.CategoryReq;
import top.aiolife.wardrobe.pojo.vo.CategoryVO;
import top.aiolife.wardrobe.service.IWardrobeCategoryService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 衣柜分类 Service 实现
 */
@Slf4j
@Service
@AllArgsConstructor
public class WardrobeCategoryServiceImpl extends ServiceImpl<WardrobeCategoryMapper, WardrobeCategoryEntity> implements IWardrobeCategoryService {

    private final WardrobeItemMapper itemMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveCategory(Long userId, CategoryReq req) {
        lockHierarchy();
        validateParent(null, req.getParentId(), userId);
        WardrobeCategoryEntity entity = reqToEntity(req);
        entity.setUserId(userId);
        entity.fillCreateCommonField(userId);
        this.save(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(Long userId, CategoryReq req) {
        lockHierarchy();
        WardrobeCategoryEntity existing = this.getById(req.getId());
        if (existing == null) {
            throw new IllegalArgumentException("分类不存在");
        }
        if (existing.getUserId() == null) {
            throw new IllegalArgumentException("系统预设分类不可修改");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new IllegalArgumentException("无权修改此分类");
        }
        validateParent(req.getId(), req.getParentId(), userId);
        WardrobeCategoryEntity entity = reqToEntity(req);
        entity.setId(req.getId());
        entity.fillUpdateCommonField(userId);
        this.updateById(entity);
        if (req.getParentId() == null) {
            this.update(new LambdaUpdateWrapper<WardrobeCategoryEntity>()
                    .eq(WardrobeCategoryEntity::getId, req.getId())
                    .set(WardrobeCategoryEntity::getParentId, null));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeCategory(Long id, Long userId) {
        lockHierarchy();
        WardrobeCategoryEntity existing = this.getById(id);
        if (existing == null) {
            throw new IllegalArgumentException("分类不存在");
        }
        if (existing.getUserId() == null) {
            throw new IllegalArgumentException("系统预设分类不可删除");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new IllegalArgumentException("无权删除此分类");
        }
        ensureNoChildren(id);
        ensureNoItems(id);
        this.removeById(id);
    }

    /** 校验父级可见且不超过两级；已有子分类的分类不能再移到其他分类下。 */
    private void validateParent(Long id, Long parentId, Long userId) {
        if (parentId == null) {
            return;
        }
        if (Objects.equals(id, parentId)) {
            throw new IllegalArgumentException("分类不能作为自己的上级");
        }
        List<WardrobeCategoryEntity> parents = this.list(visibleCategories(userId)
                .eq(WardrobeCategoryEntity::getId, parentId));
        if (parents.isEmpty()) {
            throw new IllegalArgumentException("上级分类不存在或无权使用");
        }
        WardrobeCategoryEntity parent = parents.getFirst();
        if (parent.getParentId() != null) {
            throw new IllegalArgumentException("分类最多支持两级，请选择一级分类作为上级");
        }
        if (id != null && this.count(new LambdaQueryWrapper<WardrobeCategoryEntity>()
                .eq(WardrobeCategoryEntity::getParentId, id)) > 0) {
            throw new IllegalArgumentException("该分类已有子分类，不能移动到其他分类下");
        }
    }

    private void ensureNoChildren(Long id) {
        if (this.count(new LambdaQueryWrapper<WardrobeCategoryEntity>()
                .eq(WardrobeCategoryEntity::getParentId, id)) > 0) {
            throw new IllegalArgumentException("请先移动或删除子分类");
        }
    }

    private void ensureNoItems(Long id) {
        if (itemMapper.selectCount(new LambdaQueryWrapper<WardrobeItemEntity>()
                .eq(WardrobeItemEntity::getCategoryId, id)) > 0) {
            throw new IllegalArgumentException("该分类仍有关联衣物，请先转移衣物");
        }
    }

    private void lockHierarchy() {
        baseMapper.lockHierarchy();
    }

    private LambdaQueryWrapper<WardrobeCategoryEntity> visibleCategories(Long userId) {
        return new LambdaQueryWrapper<WardrobeCategoryEntity>()
                .and(w -> w.isNull(WardrobeCategoryEntity::getUserId)
                        .or().eq(WardrobeCategoryEntity::getUserId, userId));
    }

    @Override
    public List<CategoryVO> listCategories(Long userId) {
        LambdaQueryWrapper<WardrobeCategoryEntity> queryWrapper = visibleCategories(userId);
        QueryOrder.ascNullsFirst(queryWrapper, WardrobeCategoryEntity::getSortOrder);

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
        Map<Long, WardrobeCategoryEntity> byId = new HashMap<>();
        for (WardrobeCategoryEntity entity : list) {
            byId.put(entity.getId(), entity);
        }

        Map<Long, List<WardrobeCategoryEntity>> childrenMap = new HashMap<>();
        List<WardrobeCategoryEntity> roots = new ArrayList<>();
        for (WardrobeCategoryEntity entity : list) {
            Long parentId = entity.getParentId();
            // 父级缺失或不可见时按根节点展示，避免整棵子树被静默丢弃
            if (parentId == null || !byId.containsKey(parentId) || Objects.equals(parentId, entity.getId())) {
                roots.add(entity);
            } else {
                childrenMap.computeIfAbsent(parentId, key -> new ArrayList<>()).add(entity);
            }
        }

        Set<Long> visited = new HashSet<>();
        List<CategoryVO> result = new ArrayList<>();
        for (WardrobeCategoryEntity root : roots) {
            result.add(toTree(root, childrenMap, visited));
        }
        // 兜底：环等异常数据下未访问到的节点也输出，不能因为不是根就丢失
        for (WardrobeCategoryEntity entity : list) {
            if (!visited.contains(entity.getId())) {
                result.add(toTree(entity, childrenMap, visited));
            }
        }
        return result;
    }

    private CategoryVO toTree(WardrobeCategoryEntity entity,
                              Map<Long, List<WardrobeCategoryEntity>> childrenMap,
                              Set<Long> visited) {
        CategoryVO vo = toVO(entity);
        if (!visited.add(entity.getId())) {
            return vo;
        }
        List<CategoryVO> children = childrenMap.getOrDefault(entity.getId(), List.of()).stream()
                .filter(child -> !visited.contains(child.getId()))
                .map(child -> toTree(child, childrenMap, visited))
                .collect(Collectors.toList());
        vo.setChildren(children);
        return vo;
    }

    private CategoryVO toVO(WardrobeCategoryEntity entity) {
        CategoryVO vo = new CategoryVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setIcon(entity.getIcon());
        vo.setParentId(entity.getParentId());
        vo.setSortOrder(entity.getSortOrder());
        vo.setPreset(entity.getUserId() == null);
        return vo;
    }
}
