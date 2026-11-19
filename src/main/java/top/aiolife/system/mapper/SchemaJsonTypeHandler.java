package top.aiolife.system.mapper;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** JSON 文本作为 PostgreSQL jsonb 列的参数绑定。 */
public class SchemaJsonTypeHandler extends BaseTypeHandler<String> {
    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, String value, JdbcType type)
            throws SQLException {
        statement.setObject(index, value, Types.OTHER);
    }

    @Override
    public String getNullableResult(ResultSet result, String column) throws SQLException {
        return result.getString(column);
    }

    @Override
    public String getNullableResult(ResultSet result, int column) throws SQLException {
        return result.getString(column);
    }

    @Override
    public String getNullableResult(CallableStatement statement, int column) throws SQLException {
        return statement.getString(column);
    }
}
