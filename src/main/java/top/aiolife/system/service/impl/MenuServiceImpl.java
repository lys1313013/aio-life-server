package top.aiolife.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.aiolife.system.mapper.ISysMenuMapper;
import top.aiolife.system.pojo.entity.SysMenuEntity;
import top.aiolife.system.pojo.req.MenuSaveReq;
import top.aiolife.system.pojo.vo.MenuAdminVO;
import top.aiolife.system.pojo.vo.MenuRouteVO;
import top.aiolife.system.service.IMenuService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单服务实现类
 *
 * @author Ethan
 * @date 2026/04/19
 */
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements IMenuService {

    private static final Set<String> ALLOWED_LAYOUT_COMPONENTS = Set.of("BasicLayout", "IFrameView");

    private final ISysMenuMapper sysMenuMapper;

    private final ObjectMapper objectMapper;

    @Override
    public List<MenuRouteVO> getAccessibleMenuTree(List<String> roles) {
        List<SysMenuEntity> list = listEnabledMenus();
        List<String> roleList = roles == null ? List.of() : roles;
        List<SysMenuEntity> filtered = list.stream()
                .filter(m -> isRoleAllowed(m.getRoles(), roleList))
                .toList();
        return buildTree(filtered);
    }

    @Override
    public Set<Long> getAccessibleMenuIds(List<String> roles) {
        List<SysMenuEntity> list = listEnabledMenus();
        List<String> roleList = roles == null ? List.of() : roles;
        return list.stream()
                .filter(m -> isRoleAllowed(m.getRoles(), roleList))
                .map(SysMenuEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public List<Map<String, Object>> listAccessibleLeaves(List<String> roles) {
        List<SysMenuEntity> all = listEnabledMenus();
        List<String> roleList = roles == null ? List.of() : roles;
        List<SysMenuEntity> accessible = all.stream()
                .filter(m -> isRoleAllowed(m.getRoles(), roleList))
                .toList();
        Map<Long, SysMenuEntity> byId = accessible.stream()
                .filter(m -> m.getId() != null)
                .collect(Collectors.toMap(SysMenuEntity::getId, e -> e, (a, b) -> a));
        return accessible.stream()
                .filter(this::isLeaf)
                .map(m -> toLeafMap(m, byId))
                .toList();
    }

    private boolean isLeaf(SysMenuEntity m) {
        String component = m.getComponent();
        if (!StringUtils.hasText(component)) {
            return false;
        }
        if (ALLOWED_LAYOUT_COMPONENTS.contains(component.trim())) {
            return false;
        }
        Map<String, Object> meta = readMeta(m.getMeta());
        if (Boolean.TRUE.equals(meta.get("hideInMenu"))) {
            return false;
        }
        return true;
    }

    private Map<String, Object> toLeafMap(SysMenuEntity m, Map<Long, SysMenuEntity> byId) {
        Map<String, Object> meta = readMeta(m.getMeta());
        Map<String, Object> row = new HashMap<>();
        row.put("menuId", m.getId());
        row.put("title", meta.get("title"));
        row.put("icon", meta.get("icon"));
        row.put("color", meta.get("color"));
        row.put("path", m.getPath());
        row.put("target", meta.get("link") == null ? "self" : "blank");
        row.put("parentId", m.getParentId());
        SysMenuEntity parent = m.getParentId() == null ? null : byId.get(m.getParentId());
        if (parent != null) {
            Map<String, Object> parentMeta = readMeta(parent.getMeta());
            row.put("parentTitle", parentMeta.get("title"));
        }
        return row;
    }

    @Override
    public List<MenuAdminVO> getAdminMenuTree() {
        List<SysMenuEntity> list = listAllMenus();
        return buildAdminTree(list);
    }

    @Override
    public MenuAdminVO create(MenuSaveReq req, long userId) throws Exception {
        validateReq(req, false);

        SysMenuEntity entity = new SysMenuEntity();
        fillEntity(entity, req);
        entity.fillCreateCommonField(userId);
        sysMenuMapper.insert(entity);

        return toAdminVo(entity);
    }

    @Override
    public MenuAdminVO update(long id, MenuSaveReq req, long userId) throws Exception {
        validateReq(req, true);

        SysMenuEntity exist = sysMenuMapper.selectById(id);
        if (exist == null || !Objects.equals(exist.getIsDeleted(), 0)) {
            throw new IllegalArgumentException("菜单不存在");
        }

        if (StringUtils.hasText(req.getPath()) && !Objects.equals(req.getPath(), exist.getPath())) {
            if (existsPath(req.getPath(), id)) {
                throw new IllegalArgumentException("path 已存在");
            }
        }

        fillEntity(exist, req);
        exist.fillUpdateCommonField(userId);
        sysMenuMapper.updateById(exist);
        return toAdminVo(exist);
    }

    @Override
    public MenuAdminVO updateStatus(long id, int status, long userId) throws Exception {
        if (status != 0 && status != 1) {
            throw new IllegalArgumentException("status 只能为 0 或 1");
        }
        SysMenuEntity exist = sysMenuMapper.selectById(id);
        if (exist == null || !Objects.equals(exist.getIsDeleted(), 0)) {
            throw new IllegalArgumentException("菜单不存在");
        }
        exist.setStatus(status);
        exist.fillUpdateCommonField(userId);
        sysMenuMapper.updateById(exist);
        return toAdminVo(exist);
    }

    @Override
    public MenuAdminVO updateSort(long id, int sort, long userId) throws Exception {
        SysMenuEntity exist = sysMenuMapper.selectById(id);
        if (exist == null || !Objects.equals(exist.getIsDeleted(), 0)) {
            throw new IllegalArgumentException("菜单不存在");
        }
        exist.setSort(sort);
        exist.fillUpdateCommonField(userId);
        sysMenuMapper.updateById(exist);
        return toAdminVo(exist);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(long id, long userId) {
        SysMenuEntity exist = sysMenuMapper.selectById(id);
        if (exist == null || !Objects.equals(exist.getIsDeleted(), 0)) {
            throw new IllegalArgumentException("菜单不存在或已删除");
        }

        // 核心菜单保护（系统管理 / 权限菜单禁止删除）
        if ("/system".equals(exist.getPath()) || "/system/menu".equals(exist.getPath())) {
            throw new IllegalArgumentException("核心菜单不允许删除");
        }

        // 检查是否存在子菜单
        LambdaQueryWrapper<SysMenuEntity> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.eq(SysMenuEntity::getParentId, id);
        childWrapper.eq(SysMenuEntity::getIsDeleted, 0);
        if (sysMenuMapper.selectCount(childWrapper) > 0) {
            throw new IllegalArgumentException("该菜单下存在子菜单，请先删除或移走子菜单");
        }

        // 逻辑删除
        exist.fillUpdateCommonField(userId);
        sysMenuMapper.updateById(exist);
        sysMenuMapper.deleteById(id);
    }

    private List<SysMenuEntity> listEnabledMenus() {
        LambdaQueryWrapper<SysMenuEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMenuEntity::getIsDeleted, 0);
        wrapper.eq(SysMenuEntity::getStatus, 1);
        wrapper.orderByAsc(SysMenuEntity::getSort);
        wrapper.orderByAsc(SysMenuEntity::getId);
        return sysMenuMapper.selectList(wrapper);
    }

    private List<SysMenuEntity> listAllMenus() {
        LambdaQueryWrapper<SysMenuEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMenuEntity::getIsDeleted, 0);
        wrapper.orderByAsc(SysMenuEntity::getSort);
        wrapper.orderByAsc(SysMenuEntity::getId);
        return sysMenuMapper.selectList(wrapper);
    }

    private boolean existsPath(String path, Long ignoreId) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        LambdaQueryWrapper<SysMenuEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMenuEntity::getIsDeleted, 0);
        wrapper.eq(SysMenuEntity::getPath, path);
        if (ignoreId != null) {
            wrapper.ne(SysMenuEntity::getId, ignoreId);
        }
        return sysMenuMapper.selectCount(wrapper) > 0;
    }

    private void validateReq(MenuSaveReq req, boolean isUpdate) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (!isUpdate) {
            if (!StringUtils.hasText(req.getPath())) {
                throw new IllegalArgumentException("path 不能为空");
            }
            if (existsPath(req.getPath(), null)) {
                throw new IllegalArgumentException("path 已存在");
            }
        }
        if (!StringUtils.hasText(req.getName())) {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (StringUtils.hasText(req.getPath()) && req.getPath().length() > 255) {
            throw new IllegalArgumentException("path 过长");
        }
        if (StringUtils.hasText(req.getComponent())) {
            validateComponent(req.getComponent());
        }
    }

    private void validateComponent(String component) {
        String c = component.trim();
        if (ALLOWED_LAYOUT_COMPONENTS.contains(c)) {
            return;
        }
        if (c.contains("..")) {
            throw new IllegalArgumentException("component 不合法");
        }
        if (c.startsWith("http://") || c.startsWith("https://")) {
            throw new IllegalArgumentException("component 不支持 URL");
        }
        if (!c.matches("^[a-zA-Z0-9_\\-\\/]+$")) {
            throw new IllegalArgumentException("component 不合法");
        }
    }

    private void fillEntity(SysMenuEntity entity, MenuSaveReq req) throws Exception {
        entity.setParentId(req.getParentId() == null ? 0L : req.getParentId());
        entity.setName(req.getName());
        if (StringUtils.hasText(req.getPath())) {
            entity.setPath(req.getPath());
        }
        entity.setComponent(StringUtils.hasText(req.getComponent()) ? req.getComponent().trim() : null);
        entity.setRedirect(StringUtils.hasText(req.getRedirect()) ? req.getRedirect().trim() : null);
        entity.setRoles(StringUtils.hasText(req.getRoles()) ? req.getRoles().trim() : null);
        entity.setSort(req.getSort() == null ? 0 : req.getSort());
        entity.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        entity.setMeta(req.getMeta() == null ? null : objectMapper.writeValueAsString(req.getMeta()));
    }

    private List<MenuRouteVO> buildTree(List<SysMenuEntity> list) {
        Map<Long, List<SysMenuEntity>> childrenMap = new HashMap<>();
        for (SysMenuEntity entity : list) {
            long pid = entity.getParentId() == null ? 0L : entity.getParentId();
            childrenMap.computeIfAbsent(pid, k -> new ArrayList<>()).add(entity);
        }
        for (List<SysMenuEntity> children : childrenMap.values()) {
            children.sort(Comparator.comparing(SysMenuEntity::getSort).thenComparing(SysMenuEntity::getId));
        }

        return buildChildren(0L, childrenMap);
    }

    private List<MenuRouteVO> buildChildren(long parentId, Map<Long, List<SysMenuEntity>> childrenMap) {
        List<SysMenuEntity> children = childrenMap.getOrDefault(parentId, List.of());
        List<MenuRouteVO> result = new ArrayList<>();
        for (SysMenuEntity entity : children) {
            MenuRouteVO vo = toRoute(entity);
            List<MenuRouteVO> next = buildChildren(entity.getId(), childrenMap);
            if (!next.isEmpty()) {
                vo.setChildren(next);
            }
            result.add(vo);
        }
        return result;
    }

    private List<MenuAdminVO> buildAdminTree(List<SysMenuEntity> list) {
        Map<Long, List<SysMenuEntity>> childrenMap = new HashMap<>();
        for (SysMenuEntity entity : list) {
            long pid = entity.getParentId() == null ? 0L : entity.getParentId();
            childrenMap.computeIfAbsent(pid, k -> new ArrayList<>()).add(entity);
        }
        for (List<SysMenuEntity> children : childrenMap.values()) {
            children.sort(Comparator.comparing(SysMenuEntity::getSort).thenComparing(SysMenuEntity::getId));
        }

        return buildAdminChildren(0L, childrenMap);
    }

    private List<MenuAdminVO> buildAdminChildren(long parentId, Map<Long, List<SysMenuEntity>> childrenMap) {
        List<SysMenuEntity> children = childrenMap.getOrDefault(parentId, List.of());
        List<MenuAdminVO> result = new ArrayList<>();
        for (SysMenuEntity entity : children) {
            MenuAdminVO vo = toAdminVo(entity);
            List<MenuAdminVO> next = buildAdminChildren(entity.getId(), childrenMap);
            if (!next.isEmpty()) {
                vo.setChildren(next);
            }
            result.add(vo);
        }
        return result;
    }

    private MenuRouteVO toRoute(SysMenuEntity entity) {
        MenuRouteVO vo = new MenuRouteVO();
        vo.setId(entity.getId());
        vo.setPath(entity.getPath());
        vo.setName(entity.getName());
        vo.setComponent(entity.getComponent());
        vo.setRedirect(entity.getRedirect());
        Map<String, Object> meta = readMeta(entity.getMeta());
        if (meta == null) {
            meta = new HashMap<>();
        }
        meta.put("menuId", entity.getId());
        vo.setMeta(meta);
        return vo;
    }

    private MenuAdminVO toAdminVo(SysMenuEntity entity) {
        MenuAdminVO vo = new MenuAdminVO();
        vo.setId(entity.getId());
        vo.setParentId(entity.getParentId() == null ? 0L : entity.getParentId());
        vo.setPath(entity.getPath());
        vo.setName(entity.getName());
        vo.setComponent(entity.getComponent());
        vo.setRedirect(entity.getRedirect());
        vo.setMeta(readMeta(entity.getMeta()));
        vo.setRoles(entity.getRoles());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        return vo;
    }

    private Map<String, Object> readMeta(String metaJson) {
        if (!StringUtils.hasText(metaJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(metaJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isRoleAllowed(String menuRoles, List<String> userRoles) {
        if (userRoles != null && userRoles.contains("admin")) {
            return true;
        }
        // 没有指定角色，允许所有用户访问
        if (!StringUtils.hasText(menuRoles)) {
            return true;
        }
        Set<String> allowed = parseRoles(menuRoles);
        if (allowed.isEmpty()) {
            return false;
        }
        if (userRoles == null || userRoles.isEmpty()) {
            return false;
        }
        for (String role : userRoles) {
            if (allowed.contains(role)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> parseRoles(String rolesStr) {
        if (!StringUtils.hasText(rolesStr)) {
            return Set.of();
        }
        return List.of(rolesStr.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(HashSet::new));
    }
}
