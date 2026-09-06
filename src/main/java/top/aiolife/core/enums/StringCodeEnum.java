package top.aiolife.core.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 使用字符串 code 持久化和序列化的枚举统一契约。
 */
public interface StringCodeEnum extends IEnum<String> {

    /**
     * 获取稳定的业务 code。
     */
    @JsonValue
    String getCode();

    @Override
    default String getValue() {
        return getCode();
    }
}
