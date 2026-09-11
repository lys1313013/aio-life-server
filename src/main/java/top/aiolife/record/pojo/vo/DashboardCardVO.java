package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 看板卡片VO
 *
 * @author Lys
 * @date 2025/04/13 14:26
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardCardVO {
    /**
     * 卡片类型
     */
    private String type;

    /**
     * 卡片图标
     * <a href="https://icon-sets.iconify.design/"></a> 这个网站找
     */
    private String icon = "svg:card";

    /**
     * 图标颜色（CSS 颜色值），未设置时使用前端默认颜色
     */
    private String iconColor;

    /**
     * 点击图标跳转链接
     */
    private String iconClickUrl;

    /**
     * 卡片标题
     */
    private String title;

    /**
     * 点击标题跳转链接
     */
    private String titleClickUrl;

    /**
     * 当前值
     */
    private String value;

    /**
     * 值颜色
     */
    private String valueColor;

    /**
     * 总量标题
     */
    private String totalTitle;
    /**
     * 总量值
     */
    private String totalValue;

    /**
     * 刷新间隔，单位秒
     */
    private Integer refreshInterval = 600;
}
