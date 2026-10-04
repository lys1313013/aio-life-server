package top.aiolife.record.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import top.aiolife.record.pojo.entity.MovieEntity;
import top.aiolife.record.pojo.entity.ReadRecordEntity;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;
import top.aiolife.record.pojo.query.MovieQuery;
import top.aiolife.record.pojo.query.ReadRecordQuery;
import top.aiolife.record.service.IFileService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证首页查询在数据库分页前限定归属、状态并排序。 */
class HomeCardQueryTest {
    @Test
    void 阅读首页查询限定活跃状态并优先在读且顺序稳定() {
        init(ReadRecordEntity.class);
        var service = spy(new ReadRecordServiceImpl(mock(IFileService.class)));
        doAnswer(invocation -> {
            Page<ReadRecordEntity> page = invocation.getArgument(0);
            Wrapper<ReadRecordEntity> wrapper = invocation.getArgument(1);
            assertQuery(page, wrapper);
            return page.setRecords(List.of());
        }).when(service).page(any(Page.class), any(Wrapper.class));
        try (var identity = mockStatic(StpUtil.class)) {
            identity.when(StpUtil::getLoginIdAsLong).thenReturn(88L);
            var query = new ReadRecordQuery();
            query.setActiveOnly(true);
            query.setInProgressFirst(true);
            query.setCurrent(3);
            query.setSize(20);
            service.pageList(query);
        }
    }

    @Test
    void 观影首页查询限定活跃状态并优先在看且顺序稳定() {
        init(MovieEntity.class);
        var service = spy(new MovieServiceImpl(mock(IFileService.class)));
        doAnswer(invocation -> {
            Page<MovieEntity> page = invocation.getArgument(0);
            Wrapper<MovieEntity> wrapper = invocation.getArgument(1);
            assertQuery(page, wrapper);
            return page.setRecords(List.of());
        }).when(service).page(any(Page.class), any(Wrapper.class));
        try (var identity = mockStatic(StpUtil.class)) {
            identity.when(StpUtil::getLoginIdAsLong).thenReturn(88L);
            var query = new MovieQuery();
            query.setActiveOnly(true);
            query.setInProgressFirst(true);
            query.setCurrent(3);
            query.setSize(20);
            service.pageList(query);
        }
    }

    @Test
    void 观影默认列表保持原有状态顺序并增加稳定ID兜底() {
        init(MovieEntity.class);
        var service = spy(new MovieServiceImpl(mock(IFileService.class)));
        doAnswer(invocation -> {
            Page<MovieEntity> page = invocation.getArgument(0);
            Wrapper<MovieEntity> wrapper = invocation.getArgument(1);
            assertTrue(wrapper.getSqlSegment().contains("FIELD(status, 'not_started', 'in_progress', 'completed', 'on_hold')"));
            assertTrue(wrapper.getSqlSegment().endsWith("id DESC"));
            return page.setRecords(List.of());
        }).when(service).page(any(Page.class), any(Wrapper.class));
        try (var identity = mockStatic(StpUtil.class)) {
            identity.when(StpUtil::getLoginIdAsLong).thenReturn(88L);
            service.pageList(new MovieQuery());
        }
    }

    private static void assertQuery(Page<?> page, Wrapper<?> wrapper) {
        assertEquals(3, page.getCurrent());
        assertEquals(20, page.getSize());
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("user_id ="));
        assertTrue(sql.contains("status IN"));
        assertTrue(sql.contains("FIELD(status, 'in_progress', 'not_started', 'completed', 'on_hold')"));
        assertTrue(sql.endsWith("finish_time DESC, create_time DESC, id DESC"));
        var values = ((AbstractWrapper<?, ?, ?>) wrapper)
                .getParamNameValuePairs().values();
        assertTrue(values.contains(88L));
        assertTrue(values.contains(ProgressStatusEnum.IN_PROGRESS));
        assertTrue(values.contains(ProgressStatusEnum.NOT_STARTED));
        assertFalse(values.contains(ProgressStatusEnum.COMPLETED));
        assertFalse(values.contains(ProgressStatusEnum.ON_HOLD));
    }

    private static void init(Class<?> type) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "home-card-query-test"), type);
    }
}
