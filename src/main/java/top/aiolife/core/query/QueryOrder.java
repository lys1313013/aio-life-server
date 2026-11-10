package top.aiolife.core.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.enums.SqlKeyword;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.apache.ibatis.reflection.property.PropertyNamer;

/** 显式定义可空字段的排序：正序空值在前，倒序空值在后；字段只取实体映射。 */
public final class QueryOrder {
    private QueryOrder() {}

    @SafeVarargs
    public static <T> LambdaQueryWrapper<T> descNullsLast(
            LambdaQueryWrapper<T> wrapper, SFunction<T, ?>... columns) {
        return append(wrapper, " DESC NULLS LAST", columns);
    }

    @SafeVarargs
    public static <T> LambdaQueryWrapper<T> ascNullsFirst(
            LambdaQueryWrapper<T> wrapper, SFunction<T, ?>... columns) {
        return append(wrapper, " ASC NULLS FIRST", columns);
    }

    private static <T> LambdaQueryWrapper<T> append(
            LambdaQueryWrapper<T> wrapper, String direction, SFunction<T, ?>[] columns) {
        for (var column : columns) {
            var lambda = LambdaUtils.extract(column);
            var mapping = LambdaUtils.getColumnMap(lambda.getInstantiatedClass());
            String property = PropertyNamer.methodToProperty(lambda.getImplMethodName());
            var cached = mapping == null ? null : mapping.get(LambdaUtils.formatKey(property));
            if (cached == null) throw new IllegalArgumentException("排序字段没有实体映射：" + property);
            wrapper.getExpression().add(SqlKeyword.ORDER_BY, () -> cached.getColumn() + direction);
        }
        return wrapper;
    }
}
