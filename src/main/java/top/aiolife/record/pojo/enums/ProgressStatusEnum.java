package top.aiolife.record.pojo.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import top.aiolife.core.enums.StringCodeEnum;

/**
 * 进度状态枚举
 *
 * @author Lys
 * @date 2026/06/19
 */
public enum ProgressStatusEnum implements StringCodeEnum {

    /**
     * 未开始
     */
    NOT_STARTED("not_started", "未开始"),

    /**
     * 进行中
     */
    IN_PROGRESS("in_progress", "进行中"),

    /**
     * 已完成
     */
    COMPLETED("completed", "已完成"),

    /**
     * 搁置
     */
    ON_HOLD("on_hold", "搁置");

    private final String code;

    private final String desc;

    ProgressStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    @JsonCreator
    public static ProgressStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ProgressStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知进度状态: " + code);
    }
}
