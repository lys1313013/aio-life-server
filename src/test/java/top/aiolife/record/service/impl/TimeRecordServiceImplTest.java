package top.aiolife.record.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import top.aiolife.record.mapper.ITimeRecordMapper;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.system.service.IWorkCalendarService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimeRecordServiceImplTest {

    @InjectMocks
    private TimeRecordServiceImpl timeRecordService;

    @Mock
    private ITimeRecordMapper mapper;

    @Mock
    private IWorkCalendarService calendar;

    @Test
    void testRecommendType_日历缺失不按星期猜测且不查询历史() {
        LocalDate date = LocalDate.of(2027, 1, 4);
        when(calendar.isWorkday(date)).thenReturn(null);

        assertNull(timeRecordService.recommendType(1L, date.toString(), 600, null));
        verifyNoInteractions(mapper);
        verify(calendar, never()).findPreviousComparableDate(any());
    }

    @Test
    void testRecommendType_跨年参考日缺失仍可查询已知同类日历史() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        when(calendar.isWorkday(date)).thenReturn(false);
        when(calendar.findPreviousComparableDate(date)).thenReturn(null);
        ReflectionTestUtils.setField(timeRecordService, "baseMapper", mapper);
        when(mapper.getMostFrequentCategoryAtTime(1L, 600, null, false)).thenReturn(105L);

        assertEquals(105L, timeRecordService.recommendType(1L, date.toString(), 600, null));
        verify(mapper, never()).recommendType(anyLong(), anyString(), anyInt());
    }

    @Test
    void testRecommendType_周日补班按工作日查询高频及后续分类() {
        LocalDate date = LocalDate.of(2026, 9, 20);
        when(calendar.isWorkday(date)).thenReturn(true);
        when(calendar.findPreviousComparableDate(date)).thenReturn(LocalDate.of(2026, 9, 18));
        ReflectionTestUtils.setField(timeRecordService, "baseMapper", mapper);
        when(mapper.getMostFrequentCategoryAtTime(1L, 600, null, true)).thenReturn(104L);
        when(mapper.getMostFrequentNextCategory(1L, 104L, true)).thenReturn(103L);

        assertEquals(103L, timeRecordService.recommendType(1L, date.toString(), 600, 104L));
        verify(mapper).recommendType(1L, "2026-09-18", 600);
        verify(mapper).getMostFrequentNextCategory(1L, 104L, true);
    }

    @Test
    void testSave_忽略客户端ID并返回生成ID() {
        var service = org.mockito.Mockito.spy(timeRecordService);
        var req = new top.aiolife.record.pojo.req.TimeRecordReq();
        req.setId("client-id");
        req.setStartTime(540);
        req.setEndTime(569);
        org.mockito.Mockito.doAnswer(invocation -> {
            TimeRecordEntity entity = invocation.getArgument(0);
            assertNull(entity.getId());
            assertEquals(30, entity.getDuration());
            entity.setId("2099999999999999999");
            return true;
        }).when(service).save(org.mockito.ArgumentMatchers.any(TimeRecordEntity.class));
        try (var stp = org.mockito.Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            stp.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(1L);
            assertEquals("2099999999999999999", service.saveTimeRecord(req));
        }
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
        assertEquals(13, result.getStartTime());
        assertEquals(14, result.getEndTime());
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
        assertEquals(11, result.getStartTime());
        assertEquals(40, result.getEndTime());
        assertEquals(30, result.getDuration());
    }

    @Test
    void testRecommendNextEmptyRecords() {
        // Given: empty
        List<TimeRecordEntity> records = new ArrayList<>();

        LocalDate targetDate = LocalDate.now().minusDays(1); // Not today

        // When
        TimeRecordEntity result = timeRecordService.calculateRecommendNext(records, targetDate);

        // Then: Should recommend 30 minutes (0 - 29, inclusive)
        assertEquals(0, result.getStartTime());
        assertEquals(29, result.getEndTime());
        assertEquals(30, result.getDuration());
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
        assertEquals(1431, result.getStartTime());
        assertEquals(1439, result.getEndTime());
        assertEquals(9, result.getDuration());
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

        assertEquals(1439, result.getStartTime());
        assertEquals(1439, result.getEndTime());
        assertEquals(1, result.getDuration());
    }

    @Test
    void testRecommendNext_末尾已满但中间有一分钟空隙时仍可推荐() {
        List<TimeRecordEntity> records = new ArrayList<>(List.of(
                createRecord(601, 1439), createRecord(0, 599)));

        TimeRecordEntity result = timeRecordService.calculateRecommendNext(records, LocalDate.now());

        assertEquals(600, result.getStartTime());
        assertEquals(600, result.getEndTime());
        assertEquals(1, result.getDuration());
    }

    private TimeRecordEntity createRecord(int start, int end) {
        TimeRecordEntity record = new TimeRecordEntity();
        record.setStartTime(start);
        record.setEndTime(end);
        return record;
    }
}
