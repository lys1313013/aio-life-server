package top.aiolife.record.pojo.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 前端统一使用label、value减少属性转换
 *
 * @author Lys
 * @date 2025/04/07 23:06
 */
@Getter
@Setter
public class SysDictDataVO {

    private Long id;

    /**
     * 实际存储值
     */
    private String value;

    /**
     * 显示的名称
     */
    private String label;
}
