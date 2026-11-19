package top.aiolife.system.mapper;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.StringTypeHandler;

/** JSON 文本作为 PostgreSQL jsonb 列的参数绑定。 */
public class SchemaJsonTypeHandler extends StringTypeHandler {
    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, String value, JdbcType type)
            throws SQLException {
        statement.setObject(index, value, Types.OTHER);
    }
}
