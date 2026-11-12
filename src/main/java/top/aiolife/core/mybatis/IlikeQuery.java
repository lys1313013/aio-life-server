package top.aiolife.core.mybatis;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.LambdaMeta;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * 文本检索条件：默认排序规则按字节精确比较，大小写不敏感必须显式表达。
 */
public final class IlikeQuery {

    private IlikeQuery() {
    }

    /**
     * 任一列包含关键字即命中；关键字中的通配符按字面参与匹配，与原有 like 行为一致。
     */
    @SafeVarargs
    public static <T> void anyOf(LambdaQueryWrapper<T> wrapper, String keyword, SFunction<T, ?>... columns) {
        StringBuilder sql = new StringBuilder("(");
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append(columnName(columns[i])).append(" ILIKE {0}");
        }
        wrapper.apply(sql.append(')').toString(), "%" + keyword + "%");
    }

    /**
     * 条件成立时才追加匹配条件。
     */
    @SafeVarargs
    public static <T> void anyOf(LambdaQueryWrapper<T> wrapper, boolean condition, String keyword,
                                 SFunction<T, ?>... columns) {
        if (condition) {
            anyOf(wrapper, keyword, columns);
        }
    }

    /**
     * 单列大小写不敏感等值匹配，配合 lower() 表达式唯一索引使用。
     */
    public static <T> void lowerEquals(LambdaQueryWrapper<T> wrapper, SFunction<T, ?> column, String value) {
        wrapper.apply("lower(" + columnName(column) + ") = lower({0})", value);
    }

    private static <T> String columnName(SFunction<T, ?> column) {
        LambdaMeta meta = LambdaUtils.extract(column);
        String property = propertyName(meta.getImplMethodName());
        TableInfo tableInfo = tableInfo(meta.getInstantiatedClass());
        for (TableFieldInfo field : tableInfo.getFieldList()) {
            if (field.getProperty().equals(property)) {
                return field.getColumn();
            }
        }
        throw new IllegalArgumentException("实体字段不存在：" + property);
    }

    private static TableInfo tableInfo(Class<?> entityClass) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entityClass);
        if (tableInfo == null) {
            // 纯单元测试未必经过 Mapper 扫描，按 MyBatis-Plus 的正常路径补一次元数据初始化。
            tableInfo = TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
        }
        if (tableInfo == null) {
            throw new IllegalStateException("未取得实体表信息：" + entityClass.getName());
        }
        return tableInfo;
    }

    private static String propertyName(String methodName) {
        int prefixLength;
        if (methodName.startsWith("get")) {
            prefixLength = 3;
        } else if (methodName.startsWith("is")) {
            prefixLength = 2;
        } else {
            throw new IllegalArgumentException("不是属性读取方法：" + methodName);
        }
        String property = methodName.substring(prefixLength);
        return Character.toLowerCase(property.charAt(0)) + property.substring(1);
    }
}
