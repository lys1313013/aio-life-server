package top.aiolife.record.weread;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import top.aiolife.record.mapper.UserBindMapper;
import top.aiolife.record.pojo.entity.UserBindEntity;
import top.aiolife.record.pojo.req.WereadConnectionReq;
import top.aiolife.record.service.impl.WereadServiceImpl;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WereadServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private UserBindMapper mapper;
    private WereadClient client;
    private WereadServiceImpl service;
    private AtomicLong nanos;

    @BeforeEach
    void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), UserBindEntity.class);
        mapper = mock(UserBindMapper.class);
        client = mock(WereadClient.class);
        nanos = new AtomicLong();
        service = new WereadServiceImpl(mapper, client, new WereadReadCache(nanos::get));
    }

    private UserBindEntity connection() {
        var entity = new UserBindEntity();
        entity.setId(1L);
        entity.setUserId(42L);
        entity.setPlatform("weread");
        entity.setAccessToken("wrk-test");
        entity.setMetaFields("{\"connectionVersion\":\"test-version\"}");
        return entity;
    }

    @Test
    void stats_历史周期只请求一次且透传秒级时间戳() {
        when(mapper.selectOne(any())).thenReturn(connection());
        when(client.call(anyString(), eq("/readdata/detail"), anyMap())).thenReturn(json.createObjectNode());
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            for (String mode : new String[]{"weekly", "monthly", "annually"}) {
                service.stats(mode, 1735689600L);
                verify(client).call("wrk-test", "/readdata/detail", Map.of("mode", mode, "baseTime", 1735689600L));
            }
            service.stats("overall", 1735689600L);
            verify(client).call("wrk-test", "/readdata/detail", Map.of("mode", "overall", "baseTime", 0L));
            verifyNoMoreInteractions(client);
        }
    }

    @Test
    void stats_非法日期不请求上游() {
        assertThrows(IllegalArgumentException.class, () -> service.stats("monthly", -1));
        assertThrows(IllegalArgumentException.class, () -> service.stats("annually", Long.MAX_VALUE));
        verifyNoInteractions(mapper, client);
    }

    @Test
    void credentials_请求日志和响应均不暴露明文() throws Exception {
        var request = new WereadConnectionReq();
        request.setApiKey("wrk-test-secret");
        assertFalse(JSON.toJSONString(new Object[]{request}).contains("wrk-test-secret"));
        assertFalse(json.copy().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS).writeValueAsString(request).contains("wrk-test-secret"));
        assertEquals("wrk-test-secret", json.readValue("{\"apiKey\":\"wrk-test-secret\"}", WereadConnectionReq.class).getApiKey());
    }

    @Test
    void connection_仅查询当前用户且返回对象没有凭证() throws Exception {
        when(mapper.selectOne(any())).thenReturn(connection());
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            var result = service.connection();
            assertTrue(result.connected());
            assertFalse(json.writeValueAsString(result).contains("key"));
            ArgumentCaptor<Wrapper<UserBindEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(mapper).selectOne(captor.capture());
            String sql = captor.getValue().getSqlSegment();
            assertTrue(sql.contains("user_id"));
            assertTrue(sql.contains("platform"));
            assertTrue(((AbstractWrapper<?,?,?>)captor.getValue()).getParamNameValuePairs().containsValue("weread"));
            assertTrue(((AbstractWrapper<?,?,?>)captor.getValue()).getParamNameValuePairs().containsValue(42L));
        }
    }

    @Test
    void stats_未连接用户不可读取其他用户数据() {
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(43L);
            assertThrows(IllegalStateException.class, () -> service.stats("annually"));
            verifyNoInteractions(client);
        }
    }

    @Test
    void connect_验证失败不会替换原密钥() {
        when(client.call(eq("wrk-test"), eq("/shelf/sync"), any())).thenThrow(new IllegalStateException("invalid"));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            assertThrows(IllegalStateException.class, () -> service.connect("wrk-test"));
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void sync_分页未推进时失败且不更新成功时间() throws Exception {
        when(mapper.selectOne(any())).thenReturn(connection());
        when(client.call(anyString(),eq("/shelf/sync"),any())).thenReturn(json.readTree("{\"books\":[]}"));
        when(client.call(anyString(),eq("/user/notebooks"),any())).thenReturn(json.readTree("{\"hasMore\":1,\"books\":[{\"bookId\":\"1\",\"sort\":3}]}"));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            assertThrows(IllegalStateException.class, () -> service.sync("annually"));
            verify(mapper,never()).update(isNull(),any());
            verify(client,times(2)).call(anyString(),eq("/user/notebooks"),any());
        }
    }

    @Test
    void notes_合并全部分页并按ID去重() throws Exception {
        when(mapper.selectOne(any())).thenReturn(connection());
        when(client.call(anyString(),eq("/book/bookmarklist"),any())).thenReturn(json.readTree("{\"updated\":[]}"));
        when(client.call(anyString(),eq("/review/list/mine"),eq(Map.of("bookid","123","synckey",0L,"count",20))))
            .thenReturn(json.readTree("{\"reviews\":[{\"review\":{\"reviewId\":\"a\"}}],\"hasMore\":1,\"synckey\":2}"));
        when(client.call(anyString(),eq("/review/list/mine"),eq(Map.of("bookid","123","synckey",2L,"count",20))))
            .thenReturn(json.readTree("{\"reviews\":[{\"review\":{\"reviewId\":\"a\"}},{\"review\":{\"reviewId\":\"b\"}}],\"hasMore\":0}"));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            assertEquals(2,service.notes("123").path("reviews").size());
        }
    }

    @Test
    void parameters_无效周期和书籍ID不可请求上游() {
        assertThrows(IllegalArgumentException.class, () -> service.stats("invalid"));
        assertThrows(IllegalArgumentException.class, () -> service.notes("https://example.com"));
        verifyNoInteractions(mapper,client);
    }
    @Test
    void connect_直接保存Key到账号绑定() throws Exception {
        when(client.call(eq("wrk-test"), eq("/shelf/sync"), any())).thenReturn(json.readTree("{\"books\":[]}"));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            assertTrue(service.connect("wrk-test").connected());
            ArgumentCaptor<UserBindEntity> captor = ArgumentCaptor.forClass(UserBindEntity.class);
            verify(mapper).insert(captor.capture());
            assertEquals(42L, captor.getValue().getUserId());
            assertEquals("wrk-test", captor.getValue().getAccessToken());
        }
    }

    @Test
    void sync_完整成功后才返回同步时间() throws Exception {
        when(mapper.selectOne(any())).thenReturn(connection());
        when(mapper.update(isNull(), any())).thenReturn(1);
        when(client.call(anyString(), eq("/shelf/sync"), any())).thenReturn(json.readTree("{\"books\":[]}"));
        when(client.call(anyString(), eq("/user/notebooks"), any())).thenReturn(json.readTree("{\"books\":[],\"hasMore\":0}"));
        when(client.call(anyString(), eq("/readdata/detail"), any())).thenReturn(json.readTree("{\"totalReadTime\":0}"));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            var result = service.sync("annually", 1735689600L);
            verify(client).call("wrk-test", "/readdata/detail", Map.of("mode", "annually", "baseTime", 1735689600L));
            assertTrue(result.hasNonNull("lastSyncTime"));
            assertEquals(0, result.path("stats").path("totalReadTime").asInt());
            verify(mapper).update(isNull(), any());
        }
    }

    @Test
    void connection_同步时间从绑定元数据读取() {
        var row = connection();
        row.setMetaFields("{\"lastSyncTime\":\"2026-09-11T12:30:00\",\"other\":true}");
        when(mapper.selectOne(any())).thenReturn(row);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            assertEquals(LocalDateTime.of(2026,9,11,12,30), service.connection().lastSyncTime());
        }
    }

    @Test
    void connect_更换Key只清除同步时间且保留扩展字段() throws Exception {
        var row = connection(); row.setMetaFields("{\"lastSyncTime\":\"2026-09-11T12:30:00\",\"other\":true}");
        when(mapper.selectOne(any())).thenReturn(row);
        when(client.call(anyString(),eq("/shelf/sync"),any())).thenReturn(json.readTree("{}"));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            assertNull(service.connect("wrk-new").lastSyncTime());
            assertTrue(JSON.parseObject(row.getMetaFields()).getBooleanValue("other"));
            assertFalse(JSON.parseObject(row.getMetaFields()).containsKey("lastSyncTime"));
            verify(mapper).lockWereadUser(42L);
            verify(mapper, never()).insert(any(UserBindEntity.class));
        }
    }

    @Test
    void disconnect_清除凭证后仅解绑当前用户的微信读书() {
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            service.disconnect();
            var order = inOrder(mapper);
            order.verify(mapper).lockWereadUser(42L);
            order.verify(mapper).update(isNull(),any());
            ArgumentCaptor<Wrapper<UserBindEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            order.verify(mapper).delete(captor.capture());
            assertTrue(captor.getValue().getSqlSegment().contains("platform"));
            var params = ((AbstractWrapper<?,?,?>)captor.getValue()).getParamNameValuePairs();
            assertTrue(params.containsValue(42L));
            assertTrue(params.containsValue("weread"));
        }
    }

    @Test
    void bookLink_使用当前用户凭证及原始字符串书籍ID() throws Exception {
        when(mapper.selectOne(any())).thenReturn(connection());
        String bookId = "3300144307";
        when(client.call("wrk-test", "/book/info", Map.of("bookId", bookId)))
                .thenReturn(json.readTree("{\"deepLink\":\"https://weread.qq.com/web/reader/test-book\",\"extra\":\"ignored\"}"));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            assertEquals("https://weread.qq.com/web/reader/test-book", service.bookLink(bookId).deepLink());
            verify(client).call("wrk-test", "/book/info", Map.of("bookId", bookId));
            ArgumentCaptor<Wrapper<UserBindEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(mapper).selectOne(captor.capture());
            captor.getValue().getSqlSegment();
            assertTrue(((AbstractWrapper<?,?,?>)captor.getValue()).getParamNameValuePairs().containsValue(42L));
        }
    }

    @Test
    void bookLink_非法ID及未连接用户不请求上游() {
        assertThrows(IllegalArgumentException.class, () -> service.bookLink("../bad"));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(43L);
            assertThrows(IllegalStateException.class, () -> service.bookLink("book-1"));
            verifyNoInteractions(client);
        }
    }

    @Test
    void bookLink_缺失或非官方安全链接可以重试() throws Exception {
        when(mapper.selectOne(any())).thenReturn(connection());
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            for (String link : new String[]{"", "javascript:alert(1)", "https://evil.example/book", "https://weread.qq.com.evil.example", "https://user@weread.qq.com/book", "http://weread.qq.com/book"}) {
                when(client.call(anyString(), eq("/book/info"), anyMap()))
                        .thenReturn(json.createObjectNode().put("deepLink", link));
                assertThrows(IllegalStateException.class, () -> service.bookLink("book-1"));
                nanos.addAndGet(Duration.ofSeconds(31).toNanos());
            }
            when(client.call(anyString(), eq("/book/info"), anyMap()))
                    .thenThrow(new IllegalStateException("微信读书暂时不可用，请稍后重试"))
                    .thenReturn(json.createObjectNode().put("deepLink", "https://weread.qq.com/web/reader/retry"));
            assertThrows(IllegalStateException.class, () -> service.bookLink("book-1"));
            assertThrows(IllegalStateException.class, () -> service.bookLink("book-1"));
            nanos.addAndGet(Duration.ofSeconds(31).toNanos());
            assertEquals("https://weread.qq.com/web/reader/retry", service.bookLink("book-1").deepLink());
        }
    }

    @Test
    void recent_按阅读时间排序去重并复用缓存() throws Exception {
        when(mapper.selectOne(any())).thenReturn(connection());
        when(client.call(anyString(), eq("/shelf/sync"), anyMap())).thenReturn(json.readTree("""
                {"books":[{"bookId":"0","title":"未读","readUpdateTime":0},
                {"bookId":"1","title":"书1","readUpdateTime":100},
                {"bookId":"2","title":"书2","readUpdateTime":200},
                {"bookId":"3","title":"书3","readUpdateTime":300},
                {"bookId":"3","title":"重复","readUpdateTime":300},
                {"bookId":"4","title":"书4","readUpdateTime":400,"deepLink":"https://evil.example"}]}
                """));
        when(client.call(anyString(), eq("/book/getprogress"), anyMap())).thenReturn(json.readTree("{\"book\":{\"progress\":1}}"));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            var result = service.recent();
            assertEquals(java.util.List.of("4", "3", "2", "1"), result.books().stream().map(book -> book.bookId()).toList());
            assertNull(result.nextCursor());
            assertEquals(1, result.books().getFirst().progress());
            assertNull(result.books().getFirst().deepLink());
            service.recent(); service.progress("4");
            verify(client, times(1)).call(anyString(), eq("/shelf/sync"), anyMap());
            verify(client, times(4)).call(anyString(), eq("/book/getprogress"), anyMap());
            nanos.addAndGet(Duration.ofMinutes(5).plusSeconds(1).toNanos());
            service.recent();
            verify(client, times(2)).call(anyString(), eq("/shelf/sync"), anyMap());
            verify(client, times(8)).call(anyString(), eq("/book/getprogress"), anyMap());
        }
    }

    @Test
    void recent_每批六本同秒大整数ID完整翻页且只查询当前批次进度() {
        when(mapper.selectOne(any())).thenReturn(connection());
        var shelf = json.createObjectNode();
        var books = shelf.putArray("books");
        for (int i = 12; i >= 0; i--) {
            books.addObject().put("bookId", "90071992547409" + (100 + i)).put("readUpdateTime", 200);
        }
        books.add(books.get(0).deepCopy());
        books.addObject().put("bookId", "unread").put("readUpdateTime", 0);
        when(client.call(anyString(), eq("/shelf/sync"), anyMap())).thenReturn(shelf);
        when(client.call(anyString(), eq("/book/getprogress"), anyMap())).thenReturn(
                json.createObjectNode().set("book", json.createObjectNode().put("progress", 42)));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            var first = service.recent();
            assertEquals(6, first.books().size());
            assertEquals("200:90071992547409105", first.nextCursor());
            verify(client, times(6)).call(anyString(), eq("/book/getprogress"), anyMap());
            var second = service.recent(first.nextCursor(), 6);
            assertEquals(6, second.books().size());
            assertEquals("90071992547409106", second.books().getFirst().bookId());
            assertEquals(second, service.recent(first.nextCursor(), 6));
            verify(client, times(12)).call(anyString(), eq("/book/getprogress"), anyMap());
            var last = service.recent(second.nextCursor(), 6);
            assertEquals(1, last.books().size());
            assertNull(last.nextCursor());
            verify(client, times(13)).call(anyString(), eq("/book/getprogress"), anyMap());
            verify(client, times(1)).call(anyString(), eq("/shelf/sync"), anyMap());
            assertTrue(service.recent("200:90071992547409112", 6).books().isEmpty());
        }
    }

    @Test
    void recent_无效游标和单批超限不调用外部接口() {
        for (String cursor : new String[]{"bad", "-1:book", "0:book", "9999999999999999999:book", "1:bad/id"}) {
            assertThrows(IllegalArgumentException.class, () -> service.recent(cursor, 6));
        }
        assertThrows(IllegalArgumentException.class, () -> service.recent(null, 0));
        assertThrows(IllegalArgumentException.class, () -> service.recent(null, 21));
        verifyNoInteractions(client, mapper);
    }

    @Test
    void recent_未绑定及空书架不查询进度() throws Exception {
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            assertFalse(service.recent().connected());
            verifyNoInteractions(client);
            when(mapper.selectOne(any())).thenReturn(connection());
            when(client.call(anyString(), eq("/shelf/sync"), anyMap())).thenReturn(json.readTree("{\"books\":[]}"));
            assertTrue(service.recent().connected());
            assertTrue(service.recent().books().isEmpty());
            verify(client, never()).call(anyString(), eq("/book/getprogress"), anyMap());
        }
    }

    @Test
    void recent_绑定变化和账号切换不能复用旧缓存() throws Exception {
        var first = connection();
        when(mapper.selectOne(any())).thenReturn(first);
        when(client.call(anyString(), eq("/shelf/sync"), anyMap())).thenReturn(json.readTree("{\"books\":[]}"));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            service.recent();
            var next = connection(); next.setMetaFields("{\"connectionVersion\":\"new-version\"}");
            when(mapper.selectOne(any())).thenReturn(next);
            service.recent();
            next.setUserId(43L);
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(43L);
            service.recent();
            verify(client, times(3)).call(anyString(), eq("/shelf/sync"), anyMap());
        }
    }

    @Test
    void recent_读取期间解绑不返回旧连接数据() throws Exception {
        when(mapper.selectOne(any())).thenReturn(connection(), null);
        when(client.call(anyString(), eq("/shelf/sync"), anyMap())).thenReturn(json.readTree("{\"books\":[]}"));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            assertThrows(IllegalStateException.class, service::recent);
        }
    }

}
