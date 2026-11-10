package top.aiolife.core.mybatis;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/** 布尔实体字段对应数字状态列，保持接口布尔值与数据库 0/1 的既有契约。 */
@MappedTypes({Boolean.class, boolean.class})
public class NumericBooleanTypeHandler extends BaseTypeHandler<Boolean> {
    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, Boolean value, JdbcType type)
            throws SQLException {
        statement.setInt(index, value ? 1 : 0);
    }

    @Override
    public Boolean getNullableResult(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value != 0;
    }

    @Override
    public Boolean getNullableResult(ResultSet result, int column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value != 0;
    }

    @Override
    public Boolean getNullableResult(CallableStatement statement, int column) throws SQLException {
        int value = statement.getInt(column);
        return statement.wasNull() ? null : value != 0;
    }
}
