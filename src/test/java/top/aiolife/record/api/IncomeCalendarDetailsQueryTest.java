package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.record.mapper.IIncomeMapper;
import top.aiolife.record.pojo.entity.IncomeEntity;
import top.aiolife.record.pojo.query.IncomeQuery;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncomeCalendarDetailsQueryTest {
    @Test void 明细范围排除结束边界且分页隔离归属和未填日期() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(source);
        var columns = new ArrayList<String>();
        for (var field : IncomeEntity.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            String column = field.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
            String type = field.getType() == LocalDate.class ? "DATE" : field.getType() == LocalDateTime.class ? "TIMESTAMP"
                    : field.getType() == BigDecimal.class ? "DECIMAL(14,2)"
                    : Number.class.isAssignableFrom(field.getType()) ? "BIGINT" : "VARCHAR(255)";
            columns.add(column + " " + type + (column.equals("id") ? " PRIMARY KEY" : ""));
        }
        jdbc.execute("CREATE TABLE income (" + String.join(",", columns) + ")");
        jdbc.execute("INSERT INTO income(id,user_id,amt,inc_date,is_deleted) VALUES "
                + "(1,7,10,'2026-08-12',0),(2,7,20,'2026-08-12',0),"
                + "(3,7,30,'2026-08-13',0),(4,8,999,'2026-08-12',0),"
                + "(5,7,999,'2026-08-12',1),(6,7,40,NULL,0),"
                + "(7,7,50,'2026-08-11',0)");
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(IIncomeMapper.class);
        var pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(config);
        factory.setPlugins(pagination);
        var mapper = new SqlSessionTemplate(factory.getObject()).getMapper(IIncomeMapper.class);
        var controller = new IncomeController(mapper, null);
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            var filter = new IncomeQuery();
            filter.setStartTime("2026-08-12");
            filter.setEndTimeBefore("2026-08-13");
            var query = new CommonQuery<IncomeQuery>();
            query.setCondition(filter);
            query.setPageSize(1);
            var first = controller.query(query).getData();
            assertEquals(2L, first.getTotal());
            assertEquals(2L, first.getItems().getFirst().getId());
            query.setPage(2);
            assertEquals(1L, controller.query(query).getData().getItems().getFirst().getId());
            query.setPage(3);
            assertTrue(controller.query(query).getData().getItems().isEmpty());
            filter = new IncomeQuery();
            filter.setUndated(true);
            query.setCondition(filter);
            query.setPage(1);
            var undated = controller.query(query).getData();
            assertEquals(1L, undated.getTotal());
            assertEquals(6L, undated.getItems().getFirst().getId());
            filter = new IncomeQuery();
            filter.setStartTime("2026-08-13");
            filter.setEndTime("2026-08-13");
            query.setCondition(filter);
            assertEquals(List.of(3L), controller.query(query).getData().getItems().stream().map(row -> row.getId()).toList());
        } finally {
            jdbc.execute("DROP ALL OBJECTS");
        }
    }
}
