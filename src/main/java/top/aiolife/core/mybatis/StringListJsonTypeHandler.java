package top.aiolife.core.mybatis;

import com.alibaba.fastjson2.JSON;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * jsonb 字符串数组列与实体 List<String> 互转；写入按 unknown 类型绑定，由 PostgreSQL 按列类型推断。
 */
public class StringListJsonTypeHandler extends BaseTypeHandler<List<String>> {

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, List<String> value, JdbcType type)
            throws SQLException {
        statement.setObject(index, JSON.toJSONString(value), Types.OTHER);
    }

    @Override
    public List<String> getNullableResult(ResultSet result, String column) throws SQLException {
        return parse(result.getString(column));
    }

    @Override
    public List<String> getNullableResult(ResultSet result, int column) throws SQLException {
        return parse(result.getString(column));
    }

    @Override
    public List<String> getNullableResult(CallableStatement statement, int column) throws SQLException {
        return parse(statement.getString(column));
    }

    private static List<String> parse(String json) {
        return json == null ? null : JSON.parseArray(json, String.class);
    }
}
