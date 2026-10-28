package top.aiolife.wardrobe.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.wardrobe.mapper.WardrobeCategoryMapper;
import top.aiolife.wardrobe.mapper.WardrobeItemMapper;
import top.aiolife.wardrobe.pojo.entity.WardrobeCategoryEntity;
import top.aiolife.wardrobe.pojo.entity.WardrobeItemEntity;
import top.aiolife.wardrobe.pojo.req.WardrobeItemReq;
import top.aiolife.wardrobe.pojo.vo.WardrobeItemVO;
import top.aiolife.wardrobe.pojo.vo.WardrobeStatsVO;
import top.aiolife.wardrobe.service.IWardrobeItemService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 衣柜衣物 Service 实现
 */
@Slf4j
@Service
@AllArgsConstructor
public class WardrobeItemServiceImpl extends ServiceImpl<WardrobeItemMapper, WardrobeItemEntity> implements IWardrobeItemService {

    private final WardrobeCategoryMapper categoryMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveItem(WardrobeItemReq req) {
        WardrobeItemEntity entity = reqToEntity(req);
        Long userId = StpUtil.getLoginIdAsLong();
        validateCategory(req.getCategoryId(), userId);
        entity.setUserId(userId);
        entity.fillCreateCommonField(userId);
        entity.setSeason(joinSeason(req.getSeason()));
        this.save(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateItem(WardrobeItemReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        WardrobeItemEntity existing = this.getById(req.getId());
        if (existing == null || !userId.equals(existing.getUserId())) {
            throw new RuntimeException("衣物不存在或无权限操作");
        }
        validateCategory(req.getCategoryId(), userId);
        WardrobeItemEntity entity = reqToEntity(req);
        entity.setId(req.getId());
        entity.fillUpdateCommonField(userId);
        entity.setSeason(joinSeason(req.getSeason()));
        this.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeItem(Long id, Long userId) {
        LambdaQueryWrapper<WardrobeItemEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WardrobeItemEntity::getId, id);
        queryWrapper.eq(WardrobeItemEntity::getUserId, userId);
        this.remove(queryWrapper);
    }

    @Override
    public WardrobeItemVO getItem(Long id, Long userId) {
        WardrobeItemEntity entity = this.lambdaQuery()
                .eq(WardrobeItemEntity::getId, id)
                .eq(WardrobeItemEntity::getUserId, userId)
                .one();
        if (entity == null) {
            return null;
        }
        return toVO(entity, getCategoryNameMap(userId));
    }

    @Override
    public List<WardrobeItemVO> listItems(Long userId, Long categoryId, String season, String keyword) {
        LambdaQueryWrapper<WardrobeItemEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WardrobeItemEntity::getUserId, userId);

        if (categoryId != null) {
            queryWrapper.eq(WardrobeItemEntity::getCategoryId, categoryId);
        }
        if (season != null && !season.isEmpty()) {
            queryWrapper.like(WardrobeItemEntity::getSeason, season);
        }
        if (keyword != null && !keyword.isEmpty()) {
            queryWrapper.and(w -> w.like(WardrobeItemEntity::getName, keyword)
                    .or().like(WardrobeItemEntity::getColor, keyword)
                    .or().like(WardrobeItemEntity::getBrand, keyword));
        }

        queryWrapper.orderByDesc(WardrobeItemEntity::getCreateTime);
        List<WardrobeItemEntity> list = this.list(queryWrapper);
        Map<Long, String> categoryNameMap = list.isEmpty() ? Collections.emptyMap() : getCategoryNameMap(userId);
        return list.stream().map(item -> toVO(item, categoryNameMap)).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public WardrobeStatsVO getStats(Long userId) {
        var aggregate = baseMapper.aggregateStats(userId);
        WardrobeStatsVO stats = new WardrobeStatsVO();
        stats.setTotalCount(Math.toIntExact(aggregate.totalCount()));

        // 仅合并数据库分类汇总行，保留原来的 Java 名称精确匹配规则。
        Map<String, Integer> categoryCount = new HashMap<>();
        for (var category : baseMapper.aggregateCategoryCounts(userId)) {
            String name = category.categoryName() == null ? "未分类" : category.categoryName();
            categoryCount.merge(name, Math.toIntExact(category.itemCount()), Math::addExact);
        }
        stats.setCategoryCount(categoryCount);
        stats.setSeasonCount(Map.of(
                "春", Math.toIntExact(aggregate.springCount()),
                "夏", Math.toIntExact(aggregate.summerCount()),
                "秋", Math.toIntExact(aggregate.autumnCount()),
                "冬", Math.toIntExact(aggregate.winterCount())));
        stats.setTotalValue(aggregate.totalValue());
        stats.setAvgPrice(aggregate.totalCount() == 0 ? BigDecimal.ZERO
                : aggregate.totalValue().divide(BigDecimal.valueOf(aggregate.totalCount()), 2, RoundingMode.HALF_UP));
        return stats;
    }

    private WardrobeItemEntity reqToEntity(WardrobeItemReq req) {
        WardrobeItemEntity entity = new WardrobeItemEntity();
        entity.setName(req.getName());
        entity.setCategoryId(req.getCategoryId());
        entity.setColor(req.getColor());
        entity.setBrand(req.getBrand());
        entity.setPurchaseDate(req.getPurchaseDate());
        entity.setPrice(req.getPrice());
        entity.setFileId(req.getFileId());
        entity.setSize(req.getSize());
        entity.setRemark(req.getRemark());
        return entity;
    }

    private WardrobeItemVO toVO(WardrobeItemEntity entity, Map<Long, String> categoryNameMap) {
        WardrobeItemVO vo = new WardrobeItemVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setCategoryId(entity.getCategoryId());
        vo.setCategoryName(categoryNameMap.get(entity.getCategoryId()));
        vo.setColor(entity.getColor());
        vo.setBrand(entity.getBrand());
        vo.setSeason(entity.getSeason());
        vo.setPurchaseDate(entity.getPurchaseDate());
        vo.setPrice(entity.getPrice());
        vo.setFileId(entity.getFileId());
        vo.setSize(entity.getSize());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private LambdaQueryWrapper<WardrobeCategoryEntity> visibleCategories(Long userId) {
        return new LambdaQueryWrapper<WardrobeCategoryEntity>()
                .and(w -> w.eq(WardrobeCategoryEntity::getCategoryType, 0)
                        .or(o -> o.eq(WardrobeCategoryEntity::getCategoryType, 1)
                                .eq(WardrobeCategoryEntity::getUserId, userId)));
    }

    private void validateCategory(Long categoryId, Long userId) {
        if (categoryId == null) return;
        if (categoryMapper.selectCount(visibleCategories(userId)
                .eq(WardrobeCategoryEntity::getId, categoryId)) == 0) {
            throw new IllegalArgumentException("分类不存在或无权使用");
        }
    }

    private Map<Long, String> getCategoryNameMap(Long userId) {
        List<WardrobeCategoryEntity> categories = categoryMapper.selectList(visibleCategories(userId));
        return categories.stream()
                .collect(Collectors.toMap(WardrobeCategoryEntity::getId, WardrobeCategoryEntity::getName, (a, b) -> a));
    }

    private String joinSeason(List<String> season) {
        if (season == null || season.isEmpty()) {
            return null;
        }
        return String.join(",", season);
    }

}
