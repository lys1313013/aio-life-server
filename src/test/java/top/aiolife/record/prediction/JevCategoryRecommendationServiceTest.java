package top.aiolife.record.prediction;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.aiolife.record.mapper.ITimeRecordMapper;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity;
import top.aiolife.record.service.ITimeTrackerCategoryService;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JevCategoryRecommendationServiceTest {
    @Mock JevCategoryClient client;
    @Mock ITimeRecordMapper mapper;
    @Mock ITimeTrackerCategoryService categories;
    JevCategoryRecommendationService service;
    final LocalDate date = LocalDate.of(2020, 10, 8);
    final LocalDate reference = LocalDate.of(2020, 9, 30);

    @BeforeEach
    void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TimeRecordEntity.class);
        service = new JevCategoryRecommendationService(client, mapper, categories, new RecommendationDataCache(15_000));
    }

    @Test
    void testRecommend_配置缺失不查询用户记录() {
        assertNull(service.recommend(7L, date, 600, true, reference));
        verifyNoInteractions(mapper, categories);
    }

    void enable() {
        when(client.isAvailable()).thenReturn(true);
        var category = new TimeTrackerCategoryEntity();
        category.setId(104L);
        category.setName("用户覆盖后的工作分类");
        when(categories.listUserVisibleCategories(7L)).thenReturn(List.of(category));
    }

    @Test
    void testRecommend_隔离用户并过滤目标时刻以后和不可见分类() {
        enable();
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                record(date, 540, 599, 104L), record(date, 600, 659, 104L),
                record(reference, 540, 719, 104L), record(reference, 720, 779, 999L),
                record(date.minusDays(1), 540, 599, 104L)));
        when(client.predict(any())).thenReturn(104L);
        assertEquals(104L, service.recommend(7L, date, 600, true, reference));
        var request = ArgumentCaptor.forClass(ObjectNode.class);
        verify(client).predict(request.capture());
        var state = request.getValue().path("state");
        assertTrue(state.path("target").path("isWorkday").asBoolean());
        assertEquals(1, state.path("todayRecords").size());
        assertFalse(state.has("previousCategoryId"));
        assertFalse(state.path("todayRecords").get(0).has("categoryId"));
        assertFalse(state.path("previousComparableDay").path("records").get(0).has("categoryId"));
        assertEquals("用户覆盖后的工作分类", state.path("previousCategoryName").asText());
        assertEquals("10:00", state.path("target").path("time").asText());
        assertEquals("09:00", state.path("todayRecords").get(0).path("startTime").asText());
        assertEquals("09:59", state.path("todayRecords").get(0).path("endTime").asText());
        assertEquals("用户覆盖后的工作分类", state.path("todayRecords").get(0).path("categoryName").asText());
        assertEquals(reference.toString(), state.path("previousComparableDay").path("date").asText());
        assertEquals(1, state.path("previousComparableDay").path("records").size());
        assertEquals("用户覆盖后的工作分类", request.getValue().path("questions")
                .path("current_category").path("criteria").path("104").asText());
        var query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectList(query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("user_id ="));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(7L));
        assertFalse(query.getValue().getSqlSelect().contains("title"));
    }

    @Test
    void testRecommend_无可用历史不调用模型() {
        enable();
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(record(date, 600, 659, 104L)));
        assertNull(service.recommend(7L, date, 600, false, reference));
        verify(client, never()).predict(any());
    }

    @Test
    void testRecommend_参考日缺失可使用当天此前记录() {
        enable();
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(record(date, 540, 599, 104L)));
        service.recommend(7L, date, 600, false, null);
        var request = ArgumentCaptor.forClass(ObjectNode.class);
        verify(client).predict(request.capture());
        assertFalse(request.getValue().path("state").path("target").path("isWorkday").asBoolean());
        assertTrue(request.getValue().path("state").path("previousComparableDay").path("date").isNull());
    }

    @Test
    void testRecommend_实际时钟以后的记录不得传入模型() {
        enable();
        var future = LocalDate.now().plusDays(2);
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(record(future.minusDays(1), 0, 100, 104L)));
        assertNull(service.recommend(7L, future, 600, true, future.minusDays(1)));
        verify(client, never()).predict(any());
    }

    @Test
    void testRecommend_只缓存数据库查询每次重新调用Jev() {
        enable();
        when(mapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(record(date, 540, 599, 104L)));
        when(client.predict(any())).thenReturn(104L, 105L);
        assertEquals(104L, service.recommend(7L, date, 600, true, reference));
        assertEquals(105L, service.recommend(7L, date, 600, true, reference));
        verify(client, times(2)).predict(any());
        verify(mapper, times(1)).selectList(any(LambdaQueryWrapper.class));
        verify(categories, times(1)).listUserVisibleCategories(7L);
    }

    private TimeRecordEntity record(LocalDate day, int start, int end, Long category) {
        var record = new TimeRecordEntity();
        record.setDate(day);
        record.setStartTime(start);
        record.setEndTime(end);
        record.setCategoryId(category);
        return record;
    }
}
