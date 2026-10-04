package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.aiolife.core.ApiRequestFixtures;
import top.aiolife.record.mapper.IRelaEventMapper;
import top.aiolife.record.mapper.IThoughtMapper;
import top.aiolife.record.pojo.entity.ThoughtEntity;
import top.aiolife.record.pojo.entity.ThoughtRelaEventEntity;
import top.aiolife.record.pojo.req.ThoughtUpdateReq;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ThoughtEventsContractTest {
    @BeforeAll static void initializeTables() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "records-test");
        TableInfoHelper.initTableInfo(assistant, ThoughtEntity.class);
        TableInfoHelper.initTableInfo(assistant, ThoughtRelaEventEntity.class);
    }
    private void asUser(Runnable action) {
        StpLogic original = StpUtil.getStpLogic();
        StpUtil.setStpLogic(new StpLogic("login") { @Override public long getLoginIdAsLong() { return 1L; } });
        try { action.run(); } finally { StpUtil.setStpLogic(original); }
    }
    @Test void testUpdate_未传事件保留已有事件且无权闪念不触碰事件() {
        IThoughtMapper thoughts = mock(IThoughtMapper.class); IRelaEventMapper events = mock(IRelaEventMapper.class);
        ThoughtController controller = new ThoughtController(thoughts, events, null);
        when(thoughts.update(any(ThoughtEntity.class), any(Wrapper.class))).thenReturn(1);
        asUser(() -> controller.update(2L,ApiRequestFixtures.request(new ThoughtEntity(), ThoughtUpdateReq.class))); verifyNoInteractions(events);
        when(thoughts.update(any(ThoughtEntity.class), any(Wrapper.class))).thenReturn(0);
        ThoughtEntity payload = new ThoughtEntity(); payload.setEvents(List.of());
        asUser(() -> assertNotEquals("0", controller.update(2L,ApiRequestFixtures.request(payload, ThoughtUpdateReq.class)).getRscode())); verifyNoInteractions(events);
    }
    @Test void testUpdate_空事件列表只清空当前闪念() {
        IThoughtMapper thoughts = mock(IThoughtMapper.class); IRelaEventMapper events = mock(IRelaEventMapper.class);
        ThoughtController controller = new ThoughtController(thoughts, events, null);
        when(thoughts.update(any(ThoughtEntity.class), any(Wrapper.class))).thenReturn(1);
        ThoughtEntity payload = new ThoughtEntity(); payload.setEvents(List.of()); asUser(() -> controller.update(2L,ApiRequestFixtures.request(payload, ThoughtUpdateReq.class)));
        ArgumentCaptor<Wrapper<ThoughtRelaEventEntity>> captor = ArgumentCaptor.forClass(Wrapper.class); verify(events).delete(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("thought_id =")); assertFalse(captor.getValue().getSqlSegment().contains("NOT IN"));
        assertTrue(((AbstractWrapper<?, ?, ?>) captor.getValue()).getParamNameValuePairs().containsValue(2L));
        verify(events, never()).update(any(ThoughtRelaEventEntity.class), any(Wrapper.class));
    }
    @Test void testUpdate_保留旧ID和新增ID并移除缺失事件() {
        IThoughtMapper thoughts = mock(IThoughtMapper.class); IRelaEventMapper events = mock(IRelaEventMapper.class);
        ThoughtController controller = new ThoughtController(thoughts, events, null);
        when(thoughts.update(any(ThoughtEntity.class), any(Wrapper.class))).thenReturn(1);
        when(events.update(any(ThoughtRelaEventEntity.class), any(Wrapper.class))).thenReturn(1);
        when(events.insert(any(ThoughtRelaEventEntity.class))).thenAnswer(invocation -> { invocation.<ThoughtRelaEventEntity>getArgument(0).setId(33L); return 1; });
        ThoughtRelaEventEntity existing = new ThoughtRelaEventEntity(); existing.setId(31L); existing.setContent("模拟原事件");
        ThoughtRelaEventEntity added = new ThoughtRelaEventEntity(); added.setContent("模拟新事件");
        ThoughtEntity payload = new ThoughtEntity(); payload.setEvents(List.of(existing, added)); asUser(() -> controller.update(2L,ApiRequestFixtures.request(payload, ThoughtUpdateReq.class))); assertEquals(31L, existing.getId());
        ArgumentCaptor<Wrapper<ThoughtRelaEventEntity>> captor = ArgumentCaptor.forClass(Wrapper.class); verify(events).delete(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("NOT IN"));
        assertTrue(((AbstractWrapper<?, ?, ?>) captor.getValue()).getParamNameValuePairs().values().containsAll(List.of(2L, 31L, 33L)));
    }
    @Test void testUpdate_跨闪念事件ID拒绝且不执行清理() {
        IThoughtMapper thoughts = mock(IThoughtMapper.class); IRelaEventMapper events = mock(IRelaEventMapper.class);
        ThoughtController controller = new ThoughtController(thoughts, events, null);
        when(thoughts.update(any(ThoughtEntity.class), any(Wrapper.class))).thenReturn(1);
        when(events.update(any(ThoughtRelaEventEntity.class), any(Wrapper.class))).thenReturn(0);
        ThoughtRelaEventEntity foreign = new ThoughtRelaEventEntity(); foreign.setId(99L); foreign.setContent("模拟越权事件");
        ThoughtEntity payload = new ThoughtEntity(); payload.setEvents(List.of(foreign));
        asUser(() -> assertThrows(IllegalArgumentException.class, () -> controller.update(2L,ApiRequestFixtures.request(payload, ThoughtUpdateReq.class)))); verify(events, never()).delete(any(Wrapper.class));
    }
}
