package top.aiolife.record.service.impl;

import static top.aiolife.support.MockMatchers.anyWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import cn.dev33.satoken.stp.StpUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import top.aiolife.record.mapper.ITimeRecordMapper;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity;
import top.aiolife.record.pojo.req.TimeRecordReq;
import top.aiolife.record.prediction.RecommendationDataCache;
import top.aiolife.record.service.IMovieService;
import top.aiolife.record.service.IReadRecordService;
import top.aiolife.record.service.ITimeTrackerCategoryService;
import top.aiolife.sso.service.SecondaryLockGuard;
import top.aiolife.system.service.IWorkCalendarService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimeRecordServiceImplTest {

    @Mock
    private SecondaryLockGuard secondaryLockGuard;

    @InjectMocks
    private TimeRecordServiceImpl timeRecordService;

    @Mock
    private ITimeRecordMapper mapper;

    @Mock
    private IWorkCalendarService calendar;

    @Mock
    private ITimeTrackerCategoryService categories;

    @Mock
    private IReadRecordService readRecordService;

    @Mock
    private IMovieService movieService;

    @BeforeEach
    void setupCache() {
        ReflectionTestUtils.setField(timeRecordService, "recommendationDataCache",
                new RecommendationDataCache(15000));
    }

    @Test
    void testRecommendType_日历缺失不按星期猜测且不查询历史() {
        LocalDate date = LocalDate.of(2027, 1, 4);
        when(calendar.isWorkday(date)).thenReturn(null);

        assertNull(timeRecordService.recommendType(1L, date.toString(), 600));
        verifyNoInteractions(mapper);
        verify(calendar, never()).findPreviousComparableDate(any());
    }

    @Test
    void testRecommendType_参考日缺失直接返回空分类不再查询高频() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        when(calendar.isWorkday(date)).thenReturn(false);
        when(calendar.findPreviousComparableDate(date)).thenReturn(null);
        assertNull(timeRecordService.recommendType(1L, date.toString(), 600));
        verifyNoInteractions(mapper, categories);
    }

    @Test
    void testRecommendType_参考日命中允许延续上一分类() {
        LocalDate date = LocalDate.of(2020, 9, 20);
        when(calendar.isWorkday(date)).thenReturn(true);
        when(calendar.findPreviousComparableDate(date)).thenReturn(date.minusDays(2));
        visibleCategories(104L);
        when(mapper.findReferenceRecords(1L, "2020-09-18", 600, 1440))
                .thenReturn(List.of(referenceRecord(104L)));
        assertEquals(104L, timeRecordService.recommendType(1L, date.toString(), 600));
        verify(mapper).findReferenceRecords(1L, "2020-09-18", 600, 1440);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void testRecommendType_隐藏删除分类不能回退且无可用记录时返回空() {
        visibleCategories(104L);
        when(mapper.findReferenceRecords(1L, "2020-09-18", 600, 1440))
                .thenReturn(List.of(referenceRecord(999L)));
        assertNull(referenceFallback());
    }

    @Test
    void testRecommendType_过滤不可见分类后可采用唯一有效记录() {
        visibleCategories(104L);
        when(mapper.findReferenceRecords(1L, "2020-09-18", 600, 1440))
                .thenReturn(List.of(referenceRecord(999L), referenceRecord(104L)));
        assertEquals(104L, referenceFallback());
    }

    @Test
    void testRecommendType_重叠记录返回空分类不抛单结果异常() {
        visibleCategories(104L, 105L);
        when(mapper.findReferenceRecords(1L, "2020-09-18", 600, 1440))
                .thenReturn(List.of(referenceRecord(104L), referenceRecord(105L)));
        assertNull(referenceFallback());
    }

    @Test
    void testRecommendType_没有可见分类不查询记录() {
        visibleCategories();
        assertNull(referenceFallback());
        verifyNoInteractions(mapper);
    }

    @Test
    void testRecommendType_参考日没有记录时不猜测分类() {
        visibleCategories(104L);
        when(mapper.findReferenceRecords(1L, "2020-09-18", 600, 1440)).thenReturn(List.of());
        assertNull(referenceFallback());
    }

    @Test
    void testRecommendType_未来参考日或不早于目标日均不读取() {
        var now = LocalDateTime.of(2020, 9, 19, 12, 0);
        assertNull(timeRecordService.recommendFromReferenceDay(1L, LocalDate.of(2020, 9, 22),
                600, LocalDate.of(2020, 9, 21), now));
        assertNull(timeRecordService.recommendFromReferenceDay(1L, LocalDate.of(2020, 9, 18),
                600, LocalDate.of(2020, 9, 18), now));
        verifyNoInteractions(mapper, categories);
    }

    @Test
    void testRecommendType_参考日是今天时仅查已结束记录且缓存区分截止分钟() {
        visibleCategories(104L);
        var date = LocalDate.of(2020, 9, 20);
        var now = LocalDateTime.of(2020, 9, 19, 12, 0);
        when(mapper.findReferenceRecords(1L, "2020-09-19", 600, 720)).thenReturn(List.of());
        when(mapper.findReferenceRecords(1L, "2020-09-19", 600, 721))
                .thenReturn(List.of(referenceRecord(104L)));
        assertNull(timeRecordService.recommendFromReferenceDay(1L, date, 600, now.toLocalDate(), now));
        assertEquals(104L, timeRecordService.recommendFromReferenceDay(1L, date, 600, now.toLocalDate(), now.plusMinutes(1)));
        assertNull(timeRecordService.recommendFromReferenceDay(1L, date, 720, now.toLocalDate(), now));
        verify(mapper).findReferenceRecords(1L, "2020-09-19", 600, 720);
        verify(mapper).findReferenceRecords(1L, "2020-09-19", 600, 721);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void testRecommendNext_实际查询字段包含上一分类() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                TimeRecordEntity.class);
        var service = spy(timeRecordService);
        var previous = createRecord(0, 599);
        previous.setCategoryId(104L);
        doAnswer(invocation -> {
            var query = invocation.<LambdaQueryWrapper<TimeRecordEntity>>getArgument(0);
            assertTrue(query.getSqlSelect().contains("category_id"), "必须查询分类供 Controller 读取");
            return List.of(previous);
        }).when(service).list(anyWrapper());
        var result = service.recommendNext(1L, "2020-09-19");
        assertEquals(600, result.getRecommend().getStartMinute());
        assertEquals(104L, result.getRecords().getFirst().getCategoryId());
    }

    private Long referenceFallback() {
        return timeRecordService.recommendFromReferenceDay(1L, LocalDate.of(2020, 9, 20), 600,
                LocalDate.of(2020, 9, 18), LocalDateTime.of(2020, 9, 21, 12, 0));
    }

    private void visibleCategories(Long... ids) {
        var visible = Arrays.stream(ids).map(id -> {
            var category = new TimeTrackerCategoryEntity();
            category.setId(id);
            category.setName("分类" + id);
            return category;
        }).toList();
        when(categories.listUserVisibleCategories(1L)).thenReturn(visible);
    }

    private TimeRecordEntity referenceRecord(Long categoryId) {
        var record = createRecord(540, 659);
        record.setCategoryId(categoryId);
        return record;
    }

    @Test
    void testSave_忽略客户端ID并返回生成ID() {
        var service = Mockito.spy(timeRecordService);
        var req = new TimeRecordReq();
        req.setId("client-id");
        req.setStartMinute(540);
        req.setEndMinute(569);
        Mockito.doAnswer(invocation -> {
            TimeRecordEntity entity = invocation.getArgument(0);
            assertNull(entity.getId());
            assertEquals(30, entity.getDurationMinutes());
            entity.setId(2099999999999999999L);
            return true;
        }).when(service).save(ArgumentMatchers.any(TimeRecordEntity.class));
        try (var stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            assertEquals("2099999999999999999", service.saveTimeRecord(1L, req));
        }
    }

    @Test
    void testSave_只有关联类型没有关联ID时拒绝() {
        var service = Mockito.spy(timeRecordService);
        var req = new TimeRecordReq();
        req.setStartMinute(540);
        req.setEndMinute(569);
        req.setRelateType(1);
        var exception = assertThrows(IllegalArgumentException.class,
                () -> service.saveTimeRecord(1L, req));
        assertTrue(exception.getMessage().contains("同时提交"));
        verify(service, never()).save(ArgumentMatchers.any(TimeRecordEntity.class));
    }

    @Test
    void testSave_只有关联ID没有关联类型时拒绝() {
        var service = Mockito.spy(timeRecordService);
        var req = new TimeRecordReq();
        req.setStartMinute(540);
        req.setEndMinute(569);
        req.setRelateId(123L);
        var exception = assertThrows(IllegalArgumentException.class,
                () -> service.saveTimeRecord(1L, req));
        assertTrue(exception.getMessage().contains("同时提交"));
        verify(service, never()).save(ArgumentMatchers.any(TimeRecordEntity.class));
    }

    @Test
    void testRecommendNextWithGap() {
        // Given: 0-10, 11-12, 15-20
        List<TimeRecordEntity> records = new ArrayList<>();
        records.add(createRecord(0, 10));
        records.add(createRecord(11, 12));
        records.add(createRecord(15, 20));

        LocalDate targetDate = LocalDate.now().minusDays(1); // Not today

        // When
        TimeRecordEntity result = timeRecordService.calculateRecommendNext(records, targetDate);

        // Then: Should recommend 13-14
        assertEquals(13, result.getStartMinute());
        assertEquals(14, result.getEndMinute());
    }

    @Test
    void testRecommendNextNoGapNotToday() {
        // Given: 0-10
        List<TimeRecordEntity> records = new ArrayList<>();
        records.add(createRecord(0, 10));

        LocalDate targetDate = LocalDate.now().minusDays(1); // Not today

        // When
        TimeRecordEntity result = timeRecordService.calculateRecommendNext(records, targetDate);

        // Then: Should recommend 30 minutes (11 - 40, inclusive)
        assertEquals(11, result.getStartMinute());
        assertEquals(40, result.getEndMinute());
        assertEquals(30, result.getDurationMinutes());
    }

    @Test
    void testRecommendNextEmptyRecords() {
        // Given: empty
        List<TimeRecordEntity> records = new ArrayList<>();

        LocalDate targetDate = LocalDate.now().minusDays(1); // Not today

        // When
        TimeRecordEntity result = timeRecordService.calculateRecommendNext(records, targetDate);

        // Then: Should recommend 30 minutes (0 - 29, inclusive)
        assertEquals(0, result.getStartMinute());
        assertEquals(29, result.getEndMinute());
        assertEquals(30, result.getDurationMinutes());
    }

    @Test
    void testRecommendNextNearEndOfDay() {
        // Given: Record ending at 1430 (23:50)
        List<TimeRecordEntity> records = new ArrayList<>();
        records.add(createRecord(0, 1430));

        LocalDate targetDate = LocalDate.now().minusDays(1); // Not today

        // When
        TimeRecordEntity result = timeRecordService.calculateRecommendNext(records, targetDate);

        // Then: Should recommend 1431 - 1439 (not 1461)
        assertEquals(1431, result.getStartMinute());
        assertEquals(1439, result.getEndMinute());
        assertEquals(9, result.getDurationMinutes());
    }

    @Test
    void testRecommendNext_全天已满时不推荐已占用的最后一分钟() {
        // Given: Record ending at 1439 (23:59)
        List<TimeRecordEntity> records = new ArrayList<>();
        records.add(createRecord(0, 1439));

        LocalDate targetDate = LocalDate.now().minusDays(1); // Not today

        // When
        TimeRecordEntity result = timeRecordService.calculateRecommendNext(records, targetDate);

        assertNull(result);
    }

    @Test
    void testRecommendNext_今日多条连续记录占满全天时无推荐() {
        List<TimeRecordEntity> records = new ArrayList<>(List.of(
                createRecord(720, 1439), createRecord(0, 719)));

        assertNull(timeRecordService.calculateRecommendNext(records, LocalDate.now()));
    }

    @Test
    void testRecommendNext_最后一分钟空闲时仍可推荐() {
        List<TimeRecordEntity> records = new ArrayList<>(List.of(createRecord(0, 1438)));

        TimeRecordEntity result = timeRecordService.calculateRecommendNext(records, LocalDate.now());

        assertEquals(1439, result.getStartMinute());
        assertEquals(1439, result.getEndMinute());
        assertEquals(1, result.getDurationMinutes());
    }

    @Test
    void testRecommendNext_末尾已满但中间有一分钟空隙时仍可推荐() {
        List<TimeRecordEntity> records = new ArrayList<>(List.of(
                createRecord(601, 1439), createRecord(0, 599)));

        TimeRecordEntity result = timeRecordService.calculateRecommendNext(records, LocalDate.now());

        assertEquals(600, result.getStartMinute());
        assertEquals(600, result.getEndMinute());
        assertEquals(1, result.getDurationMinutes());
    }

    private TimeRecordEntity createRecord(int start, int end) {
        TimeRecordEntity record = new TimeRecordEntity();
        record.setStartMinute(start);
        record.setEndMinute(end);
        return record;
    }
}
