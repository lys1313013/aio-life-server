package top.aiolife.record.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.aiolife.config.RedisConfig;
import top.aiolife.record.mapper.ITimeRecordMapper;
import top.aiolife.record.pojo.entity.ExerciseRecordEntity;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.req.TimeRecordReq;
import top.aiolife.record.service.IExerciseRecordService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TimeRecordDayQueryTest {
    private AnnotationConfigApplicationContext context;
    private TimeRecordServiceImpl service;
    private ITimeRecordMapper mapper;
    private CacheManager caches;
    private MockedStatic<StpUtil> login;
    private final LocalDate date = LocalDate.of(2026, 10, 4);

    @Configuration
    @EnableCaching(proxyTargetClass = true)
    static class Config implements CachingConfigurer {
        @Bean
        public CacheManager cacheManager() { return spy(new ConcurrentMapCacheManager("timeRecordDay:v1")); }
        @Override
        public CacheErrorHandler errorHandler() { return new RedisConfig().errorHandler(); }
        @Bean
        ITimeRecordMapper mapper() { return mock(ITimeRecordMapper.class); }
        @Bean
        TimeRecordServiceImpl records(ITimeRecordMapper mapper) {
            var service = spy(new TimeRecordServiceImpl(mapper, mock(IExerciseRecordService.class),
                    null, null, null, null, null, null, null));
            ReflectionTestUtils.setField(service, "baseMapper", mapper);
            ReflectionTestUtils.setField(service, "entityClass", TimeRecordEntity.class);
            return service;
        }
    }

    @BeforeEach
    void setup() {
        for (Class<?> type : List.of(TimeRecordEntity.class, ExerciseRecordEntity.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), type.getName()), type);
        }
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(TimeRecordServiceImpl.class);
        mapper = context.getBean(ITimeRecordMapper.class);
        caches = context.getBean(CacheManager.class);
        login = mockStatic(StpUtil.class);
        login.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
    }

    @AfterEach
    void cleanup() {
        login.close();
        context.close();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void testQuery_超过原分页上限完整返回且用户日期分别缓存() {
        var records = IntStream.range(0, 151).mapToObj(i -> record("row-" + i, date)).toList();
        when(mapper.selectList(any(Wrapper.class))).thenReturn(records);
        assertEquals(151, service.queryDay(1L, date).size());
        assertEquals(151, service.queryDay(1L, date).size());
        verify(mapper, times(1)).selectList(any(Wrapper.class));
        service.queryDay(2L, date);
        service.queryDay(1L, date.plusDays(1));
        verify(mapper, times(3)).selectList(any(Wrapper.class));
        var query = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper, times(3)).selectList(query.capture());
        assertTrue(query.getAllValues().getFirst().getSqlSegment().contains("user_id"));
        assertTrue(query.getAllValues().getFirst().getSqlSegment().contains("date"));
        assertFalse(query.getAllValues().getFirst().getSqlSegment().toLowerCase().contains("limit"));
    }

    @Test
    void testEmpty_空列表缓存且事务内绕过缓存() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertTrue(service.queryDay(1L, date).isEmpty());
        assertTrue(service.queryDay(1L, date).isEmpty());
        verify(mapper, times(1)).selectList(any(Wrapper.class));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        service.queryDay(1L, date);
        service.queryDay(1L, date);
        verify(mapper, times(3)).selectList(any(Wrapper.class));
    }

    @Test
    void testUpdate_事务提交后清理新旧两天且不影响其他用户() {
        warm(1L, date); warm(1L, date.plusDays(1)); warm(2L, date);
        doReturn(record("7", date)).when(service).getById("7");
        doReturn(true).when(service).updateById(any(TimeRecordEntity.class));
        TransactionSynchronizationManager.initSynchronization();
        service.updateTimeRecord(request("7", date.plusDays(1)));
        assertCached(1L, date, true);
        assertCached(1L, date.plusDays(1), true);
        commit();
        assertCached(1L, date, false);
        assertCached(1L, date.plusDays(1), false);
        assertCached(2L, date, true);
    }

    @Test
    void testSaveRollback_回滚不清理原缓存() {
        warm(1L, date);
        doAnswer(call -> { ((TimeRecordEntity) call.getArgument(0)).setId("8"); return true; })
                .when(service).save(any(TimeRecordEntity.class));
        TransactionSynchronizationManager.initSynchronization();
        assertEquals("8", service.saveTimeRecord(request(null, date)));
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertCached(1L, date, true);
    }

    @Test
    void testSave_新增后仅清理对应日期() {
        warm(1L, date); warm(1L, date.plusDays(1));
        doAnswer(call -> { ((TimeRecordEntity) call.getArgument(0)).setId("8"); return true; })
                .when(service).save(any(TimeRecordEntity.class));
        service.saveTimeRecord(request(null, date));
        assertCached(1L, date, false);
        assertCached(1L, date.plusDays(1), true);
    }

    @Test
    void testDelete_单条和整日删除清理对应日期() {
        warm(1L, date);
        doReturn(record("7", date)).when(service).getOne(any(Wrapper.class));
        doReturn(true).when(service).remove(any(Wrapper.class));
        service.removeById("7", 1L);
        assertCached(1L, date, false);
        warm(1L, date);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(record("7", date)));
        doReturn(true).when(service).removeByIds(anyCollection());
        service.removeByDate(date, 1L);
        assertCached(1L, date, false);
    }

    @Test
    void testRedisFailure_注解缓存读写失败仍返回数据库结果() {
        var failed = mock(org.springframework.cache.Cache.class);
        when(failed.getName()).thenReturn("timeRecordDay:v1");
        when(failed.get(any())).thenThrow(new IllegalStateException("Redis unavailable"));
        doThrow(new IllegalStateException("Redis unavailable")).when(failed).put(any(), any());
        doReturn(failed).when(caches).getCache("timeRecordDay:v1");
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(record("7", date)));
        assertEquals("7", service.queryDay(1L, date).getFirst().getId());
        verify(mapper, times(1)).selectList(any(Wrapper.class));
    }

    @Test
    void testRedisConfiguration_一小时过期且日期大整数往返一致() {
        var json = new top.aiolife.config.JsonConfig().jacksonObjectMapper(
                org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json());
        var manager = new RedisConfig().cacheManager(
                mock(org.springframework.data.redis.connection.RedisConnectionFactory.class), json);
        manager.afterPropertiesSet();
        var config = manager.getCacheConfigurations().get("timeRecordDay:v1");
        assertEquals(java.time.Duration.ofHours(1), config.getTtl());
        var row = top.aiolife.record.convertor.RecordApiConvertor.INSTANCE.toTimeRecordListVO(record("7", date));
        var encoded = config.getValueSerializationPair().getWriter().write(List.of(row));
        var decoded = config.getValueSerializationPair().getReader().read(encoded);
        assertEquals(List.of(row), decoded);
    }

    private void warm(long userId, LocalDate day) { caches.getCache("timeRecordDay:v1").put(userId + ":" + day, List.of()); }
    private void assertCached(long userId, LocalDate day, boolean expected) {
        assertEquals(expected, caches.getCache("timeRecordDay:v1").get(userId + ":" + day) != null);
    }
    private void commit() { TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit); }
    private TimeRecordEntity record(String id, LocalDate day) {
        var row = new TimeRecordEntity(); row.setId(id); row.setUserId(1L); row.setDate(day);
        row.setCategoryId(9223372036854775806L); row.setStartTime(540); row.setEndTime(599); return row;
    }
    private TimeRecordReq request(String id, LocalDate day) {
        var req = new TimeRecordReq(); req.setId(id); req.setDate(day); req.setStartTime(540); req.setEndTime(599); return req;
    }
}
