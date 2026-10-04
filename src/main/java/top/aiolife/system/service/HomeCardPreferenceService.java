package top.aiolife.system.service;

import top.aiolife.system.pojo.entity.HomeCardPreferenceEntity;
import top.aiolife.system.pojo.vo.HomeCardPreferenceVO;
import top.aiolife.system.mapper.HomeCardPreferenceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class HomeCardPreferenceService {
    private final HomeCardPreferenceMapper mapper;

    public List<HomeCardPreferenceVO> get(long userId) {
        Map<String, HomeCardPreferenceEntity> saved = rows(userId);
        return IntStream.range(0, HomeCardCatalog.ALL.size()).mapToObj(index -> {
            var definition = HomeCardCatalog.ALL.get(index);
            var row = saved.get(definition.cardKey());
            return new HomeCardPreferenceVO(definition.cardKey(), definition.group(), definition.title(),
                definition.icon(), row == null ? definition.defaultEnabled() : row.getEnabled(),
                row == null ? index : row.getSortOrder());
        }).sorted(Comparator.comparing(HomeCardPreferenceVO::group)
            .thenComparingInt(HomeCardPreferenceVO::sortOrder)).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public List<HomeCardPreferenceVO> toggle(long userId, String key, boolean enabled) {
        HomeCardCatalog.require(key);
        lock(userId);
        var row = getOrCreate(userId, key, rows(userId));
        row.setEnabled(enabled);
        persist(userId, row);
        return get(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<HomeCardPreferenceVO> reorder(long userId, String group, List<String> keys) {
        var expected = HomeCardCatalog.ALL.stream().filter(item -> item.group().equals(group))
            .map(HomeCardCatalog.Definition::cardKey).toList();
        if (expected.isEmpty() || keys == null || keys.size() != expected.size()
                || !new HashSet<>(keys).equals(new HashSet<>(expected))) {
            throw new IllegalArgumentException("卡片列表已变化或排序不合法，请刷新后重试");
        }
        lock(userId);
        var saved = rows(userId);
        for (int i = 0; i < keys.size(); i++) {
            var row = getOrCreate(userId, keys.get(i), saved);
            row.setSortOrder(i);
            persist(userId, row);
        }
        return get(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<HomeCardPreferenceVO> reset(long userId) {
        lock(userId);
        mapper.reset(userId);
        return get(userId);
    }

    private void lock(long userId) {
        if (mapper.lockUser(userId) == null) throw new IllegalArgumentException("用户不存在");
    }
    private Map<String, HomeCardPreferenceEntity> rows(long userId) {
        return mapper.selectList(new LambdaQueryWrapper<HomeCardPreferenceEntity>()
                .eq(HomeCardPreferenceEntity::getUserId, userId)).stream()
            .collect(Collectors.toMap(HomeCardPreferenceEntity::getCardKey, Function.identity()));
    }
    private HomeCardPreferenceEntity getOrCreate(long userId, String key, Map<String, HomeCardPreferenceEntity> saved) {
        if (saved.containsKey(key)) return saved.get(key);
        var definition = HomeCardCatalog.require(key);
        var row = new HomeCardPreferenceEntity();
        row.setUserId(userId);
        row.setCardKey(key);
        row.setEnabled(definition.defaultEnabled());
        row.setSortOrder(HomeCardCatalog.ALL.indexOf(definition));
        return row;
    }
    private void persist(long userId, HomeCardPreferenceEntity row) {
        if (row.getId() == null) {
            row.fillCreateCommonField(userId);
            mapper.insert(row);
        } else {
            row.fillUpdateCommonField(userId);
            mapper.updateById(row);
        }
    }
}
