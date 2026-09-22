package top.aiolife.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.aiolife.system.mapper.ActivityLogMapper;
import top.aiolife.system.pojo.entity.ActivityLogEntity;
import top.aiolife.system.pojo.query.ActivityLogQuery;

import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActivityLogServiceTest {
    private final ActivityLogMapper mapper = mock(ActivityLogMapper.class);
    private final ActivityLogService service = new ActivityLogService(mapper);

    @BeforeEach
    void init() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), ActivityLogEntity.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_结束日期包含整天且账号通配符按字面值过滤() {
        var query = new ActivityLogQuery();
        query.setStartDate(LocalDate.of(2026, 9, 15));
        query.setEndDate(LocalDate.of(2026, 9, 22));
        query.setUsername("a_%");
        when(mapper.selectPage(any(Page.class), any())).thenAnswer(inv -> {
            LambdaQueryWrapper<ActivityLogEntity> wrapper = inv.getArgument(1);
            String sql = wrapper.getSqlSegment();
            assertTrue(sql.contains("create_time >="));
            assertTrue(sql.contains("create_time <"));
            assertTrue(sql.contains("ORDER BY create_time DESC,id DESC"));
            var values = wrapper.getParamNameValuePairs().values();
            assertTrue(values.contains(LocalDate.of(2026, 9, 23).atStartOfDay()));
            assertTrue(values.contains("%a\\_\\%%"));
            return new Page<ActivityLogEntity>().setRecords(List.of()).setTotal(0);
        });
        assertEquals(0L, service.list("ACCESS", query).getTotal());
    }

    @Test
    void query_拒绝日期倒置和无界分页() {
        var query = new ActivityLogQuery();
        query.setPageSize(10000);
        assertThrows(IllegalArgumentException.class, () -> service.list("ACCESS", query));
        query.setPageSize(20);
        query.setStartDate(LocalDate.of(2026, 9, 22));
        query.setEndDate(LocalDate.of(2026, 9, 21));
        assertThrows(IllegalArgumentException.class, () -> service.list("ACCESS", query));
        verifyNoInteractions(mapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void export_跨批次全部导出且防公式注入() throws Exception {
        var first = LongStream.rangeClosed(2, 1001).mapToObj(i -> row(1003 - i)).toList();
        when(mapper.selectList(any())).thenReturn(List.of(row(1001)), first, List.of(row(1)), List.of());
        var writer = new StringWriter();
        service.export("OPERATION", new ActivityLogQuery(), writer);
        String csv = writer.toString();
        assertTrue(csv.startsWith("\ufeff序号,用户账号"));
        assertEquals(1002, csv.lines().count());
        assertTrue(csv.contains("\"'=SUM(1,2)\""));
        assertTrue(csv.contains("1001,"));
        verify(mapper, times(4)).selectList(any());
    }

    @Test
    void export_空数据仅导出表头() throws Exception {
        when(mapper.selectList(any())).thenReturn(List.of());
        var writer = new StringWriter();
        service.export("ACCESS", new ActivityLogQuery(), writer);
        assertTrue(writer.toString().contains("登录方式"));
        assertEquals(1, writer.toString().lines().count());
        assertEquals("\"a\"\"b,c\"", ActivityLogService.csv("a\"b,c"));
        assertEquals("\"'  =1\"", ActivityLogService.csv("  =1"));
    }

    private ActivityLogEntity row(long id) {
        var row = new ActivityLogEntity();
        row.setId(id); row.setUsername("=SUM(1,2)"); row.setSuccess(true);
        row.setCreateTime(LocalDate.of(2026, 9, 22).atStartOfDay());
        return row;
    }
}
