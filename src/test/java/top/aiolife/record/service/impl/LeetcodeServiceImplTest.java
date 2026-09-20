package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.web.client.RestTemplate;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.record.client.LeetcodeClient;
import top.aiolife.record.notification.*;
import top.aiolife.record.pojo.entity.UserBindEntity;
import top.aiolife.record.pojo.leetcode.*;
import top.aiolife.record.service.*;
import top.aiolife.record.util.RedisUtil;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LeetcodeServiceImplTest {
    private final UserMapper users = mock(UserMapper.class);
    private final IUserBindService binds = mock(IUserBindService.class);
    private final AbstractNotificationSender email = mock(AbstractNotificationSender.class);
    private final AbstractNotificationSender station = mock(AbstractNotificationSender.class);
    private final FeishuNotificationService feishu = mock(FeishuNotificationService.class);
    private final RedisUtil redis = mock(RedisUtil.class);
    private final DistributedLockExecutor locks = mock(DistributedLockExecutor.class);
    private final NotificationSendGuard guard = mock(NotificationSendGuard.class);
    private final LeetcodeServiceImpl service = spy(new LeetcodeServiceImpl(users, binds,
            mock(RestTemplate.class), List.of(email, station), feishu, redis, locks, guard,
            mock(LeetcodeClient.class), mock(CacheManager.class), new ObjectMapper()));
    private final UserEntity user = new UserEntity();

    @BeforeEach
    void setup() {
        user.setId(7L);
        UserBindEntity bind = new UserBindEntity();
        bind.setUserId(7L);
        bind.setPlatformUsername("test");
        when(users.selectById(7L)).thenReturn(user);
        when(binds.list(any(Wrapper.class))).thenReturn(List.of(bind));
        when(binds.getBindByUserIdAndPlatform(7L, "leetcode")).thenReturn(bind);
        when(email.getChannel()).thenReturn("EMAIL");
        when(station.getChannel()).thenReturn("STATION");
        when(feishu.isChannelEnabled(anyLong(), anyString(), anyString())).thenReturn(true);
        when(locks.tryRun(anyString(), any())).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return true;
        });
        doReturn("two-sum").when(service).getTodayQuestionTitleSlug();
        var submissions = new RecentACSubmissionsResponse();
        var data = new RecentACSubmissionsResponse.DataContainer();
        data.setRecentACSubmissions(List.of());
        submissions.setData(data);
        doReturn(submissions).when(service).fetchRecentAcSubmissions("test");
    }

    @Test
    void checkToday_未刷提醒按日期事件进入所有渠道去重() {
        LocalDate day = LocalDate.of(2026, 9, 20);
        try (var dates = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            dates.when(LocalDate::now).thenReturn(day);
            service.checkToday(user, true);
        }
        verify(guard).sendOnce(eq(email), eq(user), argThat(request ->
                "leetcode:reminder:2026-09-20:7".equals(request.dedupKey())), anyString());
        verify(guard).sendOnce(eq(station), eq(user), argThat(request ->
                "leetcode:reminder:2026-09-20:7".equals(request.dedupKey())), anyString());
        verify(feishu).sendIfEnabled(argThat(request -> "leetcode:reminder:2026-09-20:7".equals(request.dedupKey())));
        verify(email, never()).send(any(), any(), any(), any());
    }

    @Test
    void checkToday_仅查询或已打卡不创建通知() {
        service.checkToday(user, false);
        when(redis.hasKey(anyString())).thenReturn(true);
        service.checkToday(user, true);
        verifyNoInteractions(guard);
        verify(feishu, never()).sendIfEnabled(any());
    }

    @Test
    void notifyTodayQuestion_渠道失败不阻断其他渠道() {
        var question = new QuestionDataResponse.QuestionData();
        question.setTitleSlug("two-sum");
        question.setTranslatedTitle("两数之和");
        question.setTranslatedContent("<p>正文</p>");
        var data = new QuestionDataResponse.Data();
        data.setQuestion(question);
        var response = new QuestionDataResponse();
        response.setData(data);
        doReturn(response).when(service).getTodayQuestion();
        doThrow(new IllegalStateException("channel failure")).when(guard).sendOnce(eq(email), any(), any(), any());
        service.notifyTodayQuestion();
        verify(locks).tryRun(eq("leetcode:daily:lock"), any());
        verify(guard).sendOnce(eq(station), eq(user), argThat(request ->
                request.dedupKey().startsWith("leetcode:daily:") && request.dedupKey().endsWith(":7")), anyString());
        verify(feishu).sendIfEnabled(argThat(request -> "LEETCODE_DAILY".equals(request.bizType())));
    }

    @Test
    void checkToday_关闭的渠道不抢占发送记录() {
        when(feishu.isChannelEnabled(7L, "LEETCODE_REMINDER", "EMAIL")).thenReturn(false);
        LocalDate day = LocalDate.of(2026, 9, 20);
        try (var dates = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            dates.when(LocalDate::now).thenReturn(day);
            service.checkToday(user, true);
        }
        verify(guard, never()).sendOnce(eq(email), any(), any(), any());
        verify(guard).sendOnce(eq(station), any(), any(), any());
    }

    @Test
    void tasks_拿不到任务锁时跳过所有用户查询和发送() {
        doReturn(false).when(locks).tryRun(anyString(), any());
        service.check();
        service.notifyTodayQuestion();
        verify(locks).tryRun(eq("leetcode:check:lock"), any());
        verify(locks).tryRun(eq("leetcode:daily:lock"), any());
        verify(binds, never()).list(any(Wrapper.class));
        verifyNoInteractions(guard);
    }
}
