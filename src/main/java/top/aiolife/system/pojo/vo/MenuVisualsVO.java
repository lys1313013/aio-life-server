package top.aiolife.system.pojo.vo;

import java.util.List;
import java.util.Map;

public record MenuVisualsVO(List<MenuVisualVO> menus, Map<String, MenuVisualVO> cards) {}
