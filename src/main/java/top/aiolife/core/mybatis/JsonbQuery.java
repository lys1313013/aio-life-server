package top.aiolife.core.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

/**
 * jsonb 条件：数组列包含指定文本。
 */
public final class JsonbQuery {

    private JsonbQuery() {
    }

    public static <T> void arrayContains(LambdaQueryWrapper<T> wrapper, SFunction<T, ?> column, String value) {
        wrapper.apply("jsonb_exists(" + IlikeQuery.columnName(column) + ", {0})", value);
    }
}
