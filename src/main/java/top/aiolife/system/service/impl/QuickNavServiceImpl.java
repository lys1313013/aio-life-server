package top.aiolife.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.aiolife.system.mapper.ISysMenuMapper;
import top.aiolife.system.mapper.IUserQuickNavMapper;
import top.aiolife.system.pojo.entity.SysMenuEntity;
import top.aiolife.system.pojo.entity.UserQuickNavEntity;
import top.aiolife.system.pojo.req.QuickNavSaveReq;
import top.aiolife.system.pojo.vo.QuickNavCandidateVO;
import top.aiolife.system.pojo.vo.QuickNavItemVO;
import top.aiolife.system.service.IMenuService;
import top.aiolife.system.service.IQuickNavService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 快捷导航服务实现
 *
 * @author Ethan
 * @date 2026/06/05
 */
@Service
@RequiredArgsConstructor
public class QuickNavServiceImpl implements IQuickNavService {

    private static final int MAX_ITEMS = 12;

    private final IUserQuickNavMapper userQuickNavMapper;

    private final ISysMenuMapper sysMenuMapper;

    private final IMenuService menuService;

    private final ObjectMapper objectMapper;

    @Override
    public List<QuickNavItemVO> listMy(long userId) {
        List<UserQuickNavEntity> rows = listByUserId(userId);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> menuIds = rows.stream().map(UserQuickNavEntity::getMenuId).toList();
        Map<Long, SysMenuEntity> menuMap = loadMenuMap(menuIds);
        return rows.stream()
                .map(row -> toItemVo(row, menuMap.get(row.getMenuId())))
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<QuickNavCandidateVO> listCandidates(List<String> roles) {
        List<QuickNavCandidateVO> leaves = new ArrayList<>();
        for (Map<String, Object> leaf : menuService.listAccessibleLeaves(roles)) {
            QuickNavCandidateVO vo = new QuickNavCandidateVO();
            vo.setMenuId(asLong(leaf.get("menuId")));
            vo.setTitle((String) leaf.get("title"));
            vo.setIcon((String) leaf.get("icon"));
            vo.setColor((String) leaf.get("color"));
            vo.setPath((String) leaf.get("path"));
            vo.setTarget((String) leaf.get("target"));
            vo.setParentTitle((String) leaf.get("parentTitle"));
            leaves.add(vo);
        }
        return leaves;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<QuickNavItemVO> saveMy(long userId, List<String> roles, QuickNavSaveReq req) {
        List<QuickNavSaveReq.Item> items = req == null || req.getItems() == null
                ? List.of()
                : req.getItems();

        validateSavePayload(userId, roles, items);

        // 先逻辑删除旧布局；请求内 menuId 去重校验保证新布局不存在重复有效记录。
        userQuickNavMapper.delete(new LambdaQueryWrapper<UserQuickNavEntity>()
                .eq(UserQuickNavEntity::getUserId, userId));

        // 批量插入新记录
        if (!items.isEmpty()) {
            for (QuickNavSaveReq.Item item : items) {
                UserQuickNavEntity entity = new UserQuickNavEntity();
                entity.setUserId(userId);
                entity.setMenuId(item.getMenuId());
                entity.setSortOrder(item.getSortOrder());
                entity.setEnabled(item.getEnabled());
                entity.fillCreateCommonField(userId);
                userQuickNavMapper.insert(entity);
            }
        }

        return listMy(userId);
    }

    // ----------------- 私有辅助 -----------------

    private List<UserQuickNavEntity> listByUserId(long userId) {
        LambdaQueryWrapper<UserQuickNavEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserQuickNavEntity::getUserId, userId);
        wrapper.eq(UserQuickNavEntity::getIsDeleted, 0);
        wrapper.orderByAsc(UserQuickNavEntity::getSortOrder);
        wrapper.orderByAsc(UserQuickNavEntity::getId);
        return userQuickNavMapper.selectList(wrapper);
    }

    private Map<Long, SysMenuEntity> loadMenuMap(List<Long> menuIds) {
        if (menuIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<SysMenuEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysMenuEntity::getId, menuIds);
        wrapper.eq(SysMenuEntity::getIsDeleted, 0);
        return sysMenuMapper.selectList(wrapper).stream()
                .collect(Collectors.toMap(SysMenuEntity::getId, e -> e, (a, b) -> a));
    }

    private QuickNavItemVO toItemVo(UserQuickNavEntity row, SysMenuEntity menu) {
        if (menu == null) {
            // 菜单被删除/逻辑删除 → 跳过
            return null;
        }
        Map<String, Object> meta = readMeta(menu.getMeta());
        QuickNavItemVO vo = new QuickNavItemVO();
        vo.setMenuId(row.getMenuId());
        vo.setSortOrder(row.getSortOrder());
        vo.setEnabled(row.getEnabled());
        vo.setTitle(str(meta, "title"));
        vo.setIcon(str(meta, "icon"));
        vo.setColor(str(meta, "color"));
        vo.setPath(menu.getPath());
        vo.setTarget(targetOf(meta));
        return vo;
    }

    private void validateSavePayload(long userId, List<String> roles, List<QuickNavSaveReq.Item> items) {
        if (items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("快捷导航最多 " + MAX_ITEMS + " 项");
        }
        if (items.isEmpty()) {
            return; // 允许清空
        }
        Set<Long> seenMenu = new HashSet<>();
        Set<Integer> seenOrder = new HashSet<>();
        for (QuickNavSaveReq.Item item : items) {
            if (item.getMenuId() == null || item.getSortOrder() == null || item.getEnabled() == null) {
                throw new IllegalArgumentException("快捷导航项字段不完整");
            }
            if (item.getEnabled() != 0 && item.getEnabled() != 1) {
                throw new IllegalArgumentException("enabled 只能为 0 或 1");
            }
            if (!seenMenu.add(item.getMenuId())) {
                throw new IllegalArgumentException("菜单重复: " + item.getMenuId());
            }
            if (!seenOrder.add(item.getSortOrder())) {
                throw new IllegalArgumentException("sortOrder 重复: " + item.getSortOrder());
            }
        }
        // 校验每个 menuId 都属于当前用户可访问集合
        Set<Long> accessible = menuService.getAccessibleMenuIds(roles);
        for (Long menuId : seenMenu) {
            if (!accessible.contains(menuId)) {
                throw new IllegalArgumentException("菜单 " + menuId + " 无访问权限");
            }
        }
        // 静默引用 userId，便于后续扩展按用户差异化校验
        if (userId <= 0) {
            throw new IllegalArgumentException("用户未登录");
        }
    }

    private Map<String, Object> readMeta(String metaJson) {
        if (!StringUtils.hasText(metaJson)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(metaJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private String str(Map<String, Object> meta, String key) {
        if (meta == null) {
            return null;
        }
        Object v = meta.get(key);
        return v == null ? null : v.toString();
    }

    private String targetOf(Map<String, Object> meta) {
        if (meta == null) {
            return "self";
        }
        Object link = meta.get("link");
        return link == null ? "self" : "blank";
    }

    private Long asLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
