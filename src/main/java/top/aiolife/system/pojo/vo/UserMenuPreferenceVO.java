package top.aiolife.system.pojo.vo;

import java.util.List;

/** ID 显式以字符串返回，避免 JavaScript 雪花 ID 精度丢失。 */
public record UserMenuPreferenceVO(List<Menu> menus, List<String> hiddenMenuIds) {
    public record Menu(String id, String title, List<Menu> children) {
    }
}
