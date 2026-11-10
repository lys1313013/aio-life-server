package top.aiolife.support;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.mockito.ArgumentMatchers;

/** 为泛型数据库参数保留非空匹配语义，避免 raw Class 匹配器导致未检查的类型转换。 */
public final class MockMatchers {
    private MockMatchers() {}

    public static <T> Wrapper<T> anyWrapper() {
        return ArgumentMatchers.notNull();
    }

    public static <T> LambdaQueryWrapper<T> anyQuery() {
        return ArgumentMatchers.notNull();
    }

    public static <T> Page<T> anyPage() {
        return ArgumentMatchers.notNull();
    }
}
