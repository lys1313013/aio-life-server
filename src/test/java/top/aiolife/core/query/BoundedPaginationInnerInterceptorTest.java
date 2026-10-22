package top.aiolife.core.query;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;
import top.aiolife.config.MybatisPlusConfig;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BoundedPaginationInnerInterceptorTest {
    private final Configuration configuration = new Configuration();
    private final MappedStatement statement = new MappedStatement.Builder(configuration, "pageTest",
            new StaticSqlSource(configuration, "SELECT id FROM example"), SqlCommandType.SELECT).build();

    @Test
    void testInvalidPage_计数和查询前拒绝负数零及过大页且不执行SQL() {
        var interceptor = new BoundedPaginationInnerInterceptor();
        Executor executor = mock(Executor.class);
        for (long size : List.of(-1L, 0L, 201L, Long.MAX_VALUE)) {
            Page<Object> page = new Page<>(1, size);
            BoundSql sql = statement.getBoundSql(Map.of("page", page));
            assertThrows(IllegalArgumentException.class, () -> interceptor.willDoQuery(
                    executor, statement, Map.of("page", page), RowBounds.DEFAULT, null, sql));
            assertThrows(IllegalArgumentException.class, () -> interceptor.beforeQuery(
                    executor, statement, Map.of("page", page), RowBounds.DEFAULT, null, sql));
            assertEquals(size, page.getSize(), "不能静默截短客户端声明的页长");
        }
        Page<Object> page = new Page<>(2, 20);
        page.setCurrent(0);
        assertThrows(IllegalArgumentException.class, () -> interceptor.beforeQuery(
                executor, statement, page, RowBounds.DEFAULT, null, statement.getBoundSql(page)));
        verifyNoInteractions(executor);
    }

    @Test
    void testValidPage_真实SQL保留LIMIT及第二页偏移() {
        var interceptor = (BoundedPaginationInnerInterceptor) new MybatisPlusConfig()
                .mybatisPlusInterceptor().getInterceptors().getFirst();
        Page<Object> page = new Page<>(2, 200);
        BoundSql sql = statement.getBoundSql(page);
        interceptor.beforeQuery(null, statement, page, RowBounds.DEFAULT, null, sql);
        assertTrue(sql.getSql().contains("LIMIT ?,?"), sql.getSql());
        assertEquals(200L, sql.getAdditionalParameter(sql.getParameterMappings().get(0).getProperty()));
        assertEquals(200L, sql.getAdditionalParameter(sql.getParameterMappings().get(1).getProperty()));
        assertEquals(200, page.getSize());
    }

    @Test
    void testPerPageLimit_不能绕过全局或局部上限() {
        var interceptor = new BoundedPaginationInnerInterceptor();
        Page<Object> globalLimit = new Page<>(1, 201);
        globalLimit.setMaxLimit(1000L);
        Page<Object> localLimit = new Page<>(1, 51);
        localLimit.setMaxLimit(50L);
        for (Page<Object> page : List.of(globalLimit, localLimit)) {
            assertThrows(IllegalArgumentException.class, () -> interceptor.beforeQuery(
                    null, statement, page, RowBounds.DEFAULT, null, statement.getBoundSql(page)));
        }
    }
}
