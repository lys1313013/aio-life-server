package top.aiolife.security;

import static top.aiolife.support.MockMatchers.anyWrapper;
import cn.dev33.satoken.annotation.SaCheckRole;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.core.ApiRequestFixtures;
import top.aiolife.record.api.*;
import top.aiolife.record.mapper.*;
import top.aiolife.record.pojo.entity.*;
import top.aiolife.record.pojo.req.HonorRecordUpdateReq;
import top.aiolife.record.pojo.req.EventRecordUpdateReq;
import top.aiolife.record.pojo.req.TaskColumnSortReq;
import top.aiolife.record.pojo.req.TaskDetailSortReq;
import top.aiolife.record.pojo.req.TaskDetailUpdateReq;
import top.aiolife.record.pojo.req.TaskSortReq;
import top.aiolife.record.pojo.req.ThoughtUpdateReq;
import top.aiolife.record.service.*;
import top.aiolife.record.service.impl.TimeTrackerCategoryServiceImpl;
import top.aiolife.record.service.impl.UserDictDataServiceImpl;
import top.aiolife.record.service.UserDictDataCache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用两个虚构 owner，检查越权请求不会进入不受约束的写入/副作用。无 Spring 或外部服务。 */
class PermissionMutationTest {
    @BeforeAll
    static void tables() {
        for (Class<?> type : List.of(TaskEntity.class, TaskColumnEntity.class, TaskDetailEntity.class,
                ThoughtEntity.class, ThoughtRelaEventEntity.class, EventRecordEntity.class, FileEntity.class,
                HonorRecordEntity.class, TimeTrackerCategoryEntity.class, UserDictDataEntity.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), type.getName()), type);
        }
    }

    @Test
    void privateCategory_cannotBecomePublicOrAnotherUsersRecord() {
        for (long injectedOwner : List.of(0L, 22L)) {
            var service = spy(new TimeTrackerCategoryServiceImpl(mock(TimeTrackerCategoryCache.class)));
            doNothing().when(service).lockHierarchy();
            var existing = new TimeTrackerCategoryEntity();
            existing.setId(7L); existing.setUserId(11L);
            var updates = new TimeTrackerCategoryEntity();
            updates.setUserId(injectedOwner); updates.setTemplateId(99L); updates.setName("本人分类");
            doReturn(existing).when(service).getById(7L);
            doReturn(true).when(service).update(any(TimeTrackerCategoryEntity.class), anyWrapper());
            service.updateCategory(7L, updates, 11L);
            assertEquals(11L, updates.getUserId());
            assertNull(updates.getTemplateId());
            ArgumentCaptor<Wrapper<TimeTrackerCategoryEntity>> query = ArgumentCaptor.captor();
            verify(service).update(eq(updates), query.capture());
            assertTrue(query.getValue().getSqlSegment().contains("user_id"));
            verify(service, never()).updateById(any(TimeTrackerCategoryEntity.class));
        }
    }

    @Test
    void privateDictionary_cannotChangeOwnerOrTemplate() {
        var service = spy(new UserDictDataServiceImpl(mock(UserDictDataCache.class)));
        var existing = new UserDictDataEntity();
        existing.setId(7L); existing.setUserId(11L);
        var updates = new UserDictDataEntity();
        updates.setUserId(0L); updates.setTemplateId(99L);
        doReturn(existing).when(service).getById(7L);
        doReturn(true).when(service).update(any(UserDictDataEntity.class), anyWrapper());
        service.updateDictData(7L, updates, 11L);
        assertEquals(11L, updates.getUserId());
        assertNull(updates.getTemplateId());
        verify(service, never()).updateById(any(UserDictDataEntity.class));
    }

    @Test
    void foreignParentCategory_isRejectedBeforeWriting() {
        var service = spy(new TimeTrackerCategoryServiceImpl(mock(TimeTrackerCategoryCache.class)));
        var foreign = new TimeTrackerCategoryEntity(); foreign.setUserId(22L);
        doReturn(foreign).when(service).getById(7L);
        assertThrows(RuntimeException.class, () -> service.updateCategory(7L, new TimeTrackerCategoryEntity(), 11L));
        verify(service, never()).update(any(TimeTrackerCategoryEntity.class), anyWrapper());
    }

    @Test
    void sorting_doesNotWriteInjectedOwnerOrContent() {
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            var mapper = mock(ITaskMapper.class);
            var taskService = new top.aiolife.record.service.impl.TaskServiceImpl(mock(ITaskDetail.class));
            org.springframework.test.util.ReflectionTestUtils.setField(taskService, "baseMapper", mapper);
            var controller = new TaskController(taskService);
            var input = new TaskEntity(); input.setId(7L); input.setUserId(22L);
            input.setContent("不要写入"); input.setCreateUser(22L); input.setSortOrder(3);
            controller.reSort("11", ApiRequestFixtures.requests(List.of(input), TaskSortReq.class));
            var saved = ArgumentCaptor.forClass(TaskEntity.class);
            ArgumentCaptor<Wrapper<TaskEntity>> query = ArgumentCaptor.captor();
            verify(mapper).update(saved.capture(), query.capture());
            assertNull(saved.getValue().getUserId()); assertNull(saved.getValue().getCreateUser());
            assertNull(saved.getValue().getContent()); assertEquals(3, saved.getValue().getSortOrder());
            assertTrue(query.getValue().getSqlSegment().contains("user_id"));
        }
    }

    @Test
    void columnAndDetailSorting_onlyWriteSortFields() {
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            var columns = mock(ITaskColumnMapper.class);
            var columnService = new top.aiolife.record.service.impl.TaskColumnServiceImpl();
            org.springframework.test.util.ReflectionTestUtils.setField(columnService, "baseMapper", columns);
            var column = new TaskColumnEntity(); column.setId(7L); column.setUserId(22L); column.setSortOrder(4);
            new TaskColumnController(columnService).reSort("11", ApiRequestFixtures.requests(List.of(column), TaskColumnSortReq.class));
            var savedColumn = ArgumentCaptor.forClass(TaskColumnEntity.class);
            verify(columns).update(savedColumn.capture(), anyWrapper());
            assertNull(savedColumn.getValue().getUserId());
            assertEquals(4, savedColumn.getValue().getSortOrder());

            var details = mock(ITaskDetailMapper.class);
            var detailService = new top.aiolife.record.service.impl.TaskDetailImpl(mock(ITaskMapper.class));
            org.springframework.test.util.ReflectionTestUtils.setField(detailService, "baseMapper", details);
            var detail = new TaskDetailEntity(); detail.setId(8L); detail.setUserId(22L);
            detail.setTaskId(99L); detail.setContent("不要写入"); detail.setSortOrder(5);
            new TaskDetailController(detailService).reSort("11", ApiRequestFixtures.requests(List.of(detail), TaskDetailSortReq.class));
            var savedDetail = ArgumentCaptor.forClass(TaskDetailEntity.class);
            verify(details).update(savedDetail.capture(), anyWrapper());
            assertNull(savedDetail.getValue().getUserId()); assertNull(savedDetail.getValue().getTaskId());
            assertNull(savedDetail.getValue().getContent()); assertEquals(5, savedDetail.getValue().getSortOrder());
        }
    }

    @Test
    void detailUpdate_keepsAuthenticatedOwner() {
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            var detailMapper = mock(ITaskDetailMapper.class);
            var service = new top.aiolife.record.service.impl.TaskDetailImpl(mock(ITaskMapper.class));
            ReflectionTestUtils.setField(service, "baseMapper", detailMapper);
            var controller = new TaskDetailController(service);
            var input = new TaskDetailEntity(); input.setId(7L); input.setUserId(22L);
            controller.update("11", ApiRequestFixtures.request(input, TaskDetailUpdateReq.class));
            var saved = ArgumentCaptor.forClass(TaskDetailEntity.class);
            ArgumentCaptor<Wrapper<TaskDetailEntity>> query = ArgumentCaptor.captor();
            verify(detailMapper).update(saved.capture(), query.capture());
            assertEquals(11L, saved.getValue().getUserId());
            assertEquals(7L, saved.getValue().getId());
            assertTrue(query.getValue().getSqlSegment().contains("user_id"));
        }
    }

    @Test
    void foreignThoughtEvent_isNeverUpsertedOrReparented() {
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            var thoughts = mock(IThoughtMapper.class); var events = mock(IRelaEventMapper.class);
            when(thoughts.update(any(ThoughtEntity.class), anyWrapper())).thenReturn(1);
            when(events.update(any(ThoughtRelaEventEntity.class), anyWrapper())).thenReturn(0);
            var thoughtService = new top.aiolife.record.service.impl.ThoughtServiceImpl(thoughts, events);
            var controller = new ThoughtController(thoughtService, mock(ThoughtCreationService.class));
            var input = new ThoughtEntity(); var event = new ThoughtRelaEventEntity();
            event.setId(99L); event.setThoughtId(88L); event.setContent("外来事件"); input.setEvents(List.of(event));
            assertThrows(IllegalArgumentException.class, () -> controller.update("11", "7", ApiRequestFixtures.request(input, ThoughtUpdateReq.class)));
            var saved = ArgumentCaptor.forClass(ThoughtRelaEventEntity.class);
            ArgumentCaptor<Wrapper<ThoughtRelaEventEntity>> query = ArgumentCaptor.captor();
            verify(events).update(saved.capture(), query.capture());
            assertNull(saved.getValue().getThoughtId());
            assertTrue(query.getValue().getSqlSegment().contains("thought_id"));
            verify(events, never()).insertOrUpdate(any(ThoughtRelaEventEntity.class));
            verify(events, never()).insert(any(ThoughtRelaEventEntity.class));
        }
    }

    @Test
    void missingBusinessUpdate_neverBindsAttachments() {
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            var files = mock(IFileService.class);
            var eventRecord = new EventRecordEntity(); eventRecord.setId(99L);
            eventRecord.setCreateUser(22L); eventRecord.setFileIds(List.of("dummy-file"));
            var eventRecordMapper = mock(IEventRecordMapper.class);
            assertThrows(IllegalArgumentException.class, () ->
                    new EventRecordController(new EventRecordService(eventRecordMapper, files))
                            .updateEventRecord("11", ApiRequestFixtures.request(eventRecord, EventRecordUpdateReq.class)));
            var eventRecordUpdate = ArgumentCaptor.forClass(EventRecordEntity.class);
            verify(eventRecordMapper).update(eventRecordUpdate.capture(), anyWrapper());
            assertNull(eventRecordUpdate.getValue().getCreateUser());
            assertNull(eventRecordUpdate.getValue().getUserId());
            var honor = new HonorRecordEntity(); honor.setId(99L); honor.setFileIds(List.of("dummy-file"));
            var honorService = new top.aiolife.record.service.impl.HonorRecordServiceImpl(files);
            ReflectionTestUtils.setField(honorService, "baseMapper", mock(IHonorRecordMapper.class));
            assertThrows(IllegalArgumentException.class, () ->
                    new HonorRecordController(honorService)
                            .updateHonorRecord("11", ApiRequestFixtures.request(honor, HonorRecordUpdateReq.class)));
            verifyNoInteractions(files);
        }
    }

    @Test
    void successfulBusinessUpdate_stillBindsOwnAttachments() {
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            var files = mock(IFileService.class); var mapper = mock(IEventRecordMapper.class);
            when(mapper.update(any(EventRecordEntity.class), anyWrapper())).thenReturn(1);
            // 附件同步会先查询当前用户拥有的文件，再执行绑定。
            var ownedFile = new FileEntity(); ownedFile.setId("31"); ownedFile.setCreateUser(11L);
            when(files.list(anyWrapper())).thenReturn(List.of(ownedFile));
            var input = new EventRecordEntity(); input.setId(7L); input.setFileIds(List.of("31"));
            new EventRecordController(new EventRecordService(mapper, files))
                    .updateEventRecord("11", ApiRequestFixtures.request(input, EventRecordUpdateReq.class));
            ArgumentCaptor<Wrapper<FileEntity>> query = ArgumentCaptor.captor();
            verify(files).list(query.capture());
            assertTrue(query.getValue().getSqlSegment().contains("id IN"));
            assertTrue(query.getValue().getSqlSegment().contains("create_user ="));
            assertTrue(((AbstractWrapper<?, ?, ?>) query.getValue()).getParamNameValuePairs()
                    .values().containsAll(List.of("31", 11L)));
            verify(files).bindBizId(List.of("31"), "event_record", 7L);
        }
    }

    @Test
    void globalNotification_requiresAdmin() throws Exception {
        var role = LeetcodeAdminController.class.getAnnotation(SaCheckRole.class);
        assertNotNull(role); assertArrayEquals(new String[]{"admin"}, role.value());
        assertNotNull(top.aiolife.record.service.impl.ThoughtServiceImpl.class
                .getMethod("updateForUser", long.class, long.class, ThoughtEntity.class)
                .getAnnotation(Transactional.class));
    }
}
