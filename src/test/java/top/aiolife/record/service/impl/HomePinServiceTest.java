package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Answers;
import org.springframework.test.util.ReflectionTestUtils;
import top.aiolife.record.mapper.IAnniversaryRecordMapper;
import top.aiolife.record.mapper.IGoalMapper;
import top.aiolife.record.pojo.entity.AnniversaryRecordEntity;
import top.aiolife.record.pojo.entity.GoalEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 两种首页固定业务共用的行为契约；Mapper 全部使用 mock，不启动数据库。 */
class HomePinServiceTest {
    private static final long USER = 42L;

    @ParameterizedTest
    @EnumSource(Kind.class)
    void create_默认不固定且覆盖客户端归属与排序(Kind kind) {
        Fixture f = new Fixture(kind);
        Object entity = f.entity(10L, null, -999);
        ReflectionTestUtils.setField(entity, "userId", 99L);
        assertSame(entity, f.call("createForUser", USER, entity));
        assertPin(entity, 0, 0);
        assertEquals(USER, field(entity, "userId"));
        assertEquals(0, field(entity, "isDeleted"));
        assertEquals(USER, field(entity, "createUser"));
        assertNotNull(field(entity, "createTime"));
        assertEquals(List.of("lockPinOwner", "insert"), f.names());
        f.assertLockFirst();
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void create_新固定排在已有记录之前(Kind kind) {
        Fixture f = new Fixture(kind);
        f.pinned = List.of(f.entity(1L, 1, -5), f.entity(2L, 1, 3));
        Object entity = f.entity(10L, 1, 999);
        f.call("createForUser", USER, entity);
        assertPin(entity, 1, -6);
        assertEquals(List.of("lockPinOwner", "selectList", "insert"), f.names());
        f.assertPinnedQuery();
        f.assertLockFirst();
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void setPinned_重复固定不改变排序(Kind kind) {
        Fixture f = new Fixture(kind);
        f.owned = f.entity(10L, 1, -8);
        f.call("setPinned", USER, 10L, 1);
        assertPin(f.updates().getFirst().args()[0], 1, -8);
        assertFalse(f.names().contains("selectList"));
        f.assertOwnedScopes(10L);
        f.assertLockFirst();
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void update_省略固定字段保留固定状态和原排序(Kind kind) {
        Fixture f = new Fixture(kind);
        f.owned = f.entity(10L, 1, -8);
        Object edit = f.entity(10L, null, 999);
        ReflectionTestUtils.setField(edit, "userId", 99L);
        f.call("updateForUser", USER, edit);
        assertPin(edit, 1, -8);
        assertNull(field(edit, "userId"));
        assertEquals(USER, field(edit, "updateUser"));
        assertFalse(f.names().contains("selectList"));
        f.assertOwnedScopes(10L);
        f.assertLockFirst();
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void setPinned_取消归零再固定排到最前(Kind kind) {
        Fixture f = new Fixture(kind);
        f.owned = f.entity(10L, 1, -8);
        f.call("setPinned", USER, 10L, 0);
        Object cancelled = f.updates().getFirst().args()[0];
        assertPin(cancelled, 0, 0);
        f.assertLockFirst();
        f.calls.clear();
        f.owned = f.entity(10L, 0, 0);
        f.pinned = List.of(f.entity(2L, 1, -11));
        f.call("setPinned", USER, 10L, 1);
        assertPin(f.updates().getFirst().args()[0], 1, -12);
        f.assertPinnedQuery();
        f.assertOwnedScopes(10L);
        f.assertLockFirst();
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void setPinned_不存在或其他用户记录不能写入(Kind kind) {
        Fixture f = new Fixture(kind);
        assertThrows(IllegalArgumentException.class, () -> f.call("setPinned", USER, 99L, 1));
        assertTrue(f.updates().isEmpty());
        f.assertOwnedScopes(99L);
        f.assertLockFirst();
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void reorder_拒绝外人未固定或遗漏记录且不进行部分写入(Kind kind) {
        // 99 为外人记录，3 为本人的未固定记录；二者均不在固定列表查询结果内。
        for (List<Long> ids : List.of(List.of(1L, 99L), List.of(1L, 3L), List.of(1L))) {
            Fixture f = new Fixture(kind);
            f.pinned = List.of(f.entity(1L, 1, 0), f.entity(2L, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> f.call("reorderPinned", USER, ids));
            assertTrue(f.updates().isEmpty());
            f.assertPinnedQuery();
            f.assertLockFirst();
        }
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void reorder_拒绝重复和空ID(Kind kind) {
        List<List<Long>> invalid = Arrays.asList(null, List.of(1L, 1L), Arrays.asList(1L, null));
        for (List<Long> ids : invalid) {
            Fixture f = new Fixture(kind);
            assertThrows(IllegalArgumentException.class, () -> f.call("reorderPinned", USER, ids));
            assertEquals(List.of("lockPinOwner"), f.names());
        }
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void reorder_按完整请求顺序保存且限定归属和固定状态(Kind kind) {
        Fixture f = new Fixture(kind);
        f.pinned = List.of(f.entity(1L, 1, -8), f.entity(2L, 1, 4));
        f.call("reorderPinned", USER, List.of(2L, 1L));
        assertEquals(2, f.updates().size());
        for (int i = 0; i < 2; i++) {
            Call update = f.updates().get(i);
            assertNull(update.args()[0]);
            LambdaUpdateWrapper<?> wrapper = (LambdaUpdateWrapper<?>) update.args()[1];
            assertScope(wrapper, i == 0 ? 2L : 1L);
            assertTrue(sql(wrapper).contains("is_pinned = 1"));
            assertTrue(setSql(wrapper).contains("pinned_sort=" + i));
        }
        f.assertPinnedQuery();
        f.assertLockFirst();
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void create_排序下溢前重排已有固定项(Kind kind) {
        Fixture f = new Fixture(kind);
        f.pinned = List.of(f.entity(1L, 1, Integer.MIN_VALUE), f.entity(2L, 1, -2));
        Object entity = f.entity(10L, 1, null);
        f.call("createForUser", USER, entity);
        assertPin(entity, 1, -1);
        assertEquals(2, f.updates().size());
        assertTrue(setSql((LambdaUpdateWrapper<?>) f.updates().get(0).args()[1]).contains("pinned_sort=0"));
        assertTrue(setSql((LambdaUpdateWrapper<?>) f.updates().get(1).args()[1]).contains("pinned_sort=1"));
        assertEquals("insert", f.names().getLast());
        f.assertLockFirst();
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void delete_逻辑删除并清除固定且限制用户和未删除范围(Kind kind) {
        Fixture f = new Fixture(kind);
        f.call("deleteForUser", USER, List.of("10", "11"));
        assertEquals(List.of("lockPinOwner", "update"), f.names());
        LambdaUpdateWrapper<?> wrapper = (LambdaUpdateWrapper<?>) f.updates().getFirst().args()[1];
        String condition = sql(wrapper);
        assertTrue(condition.contains("user_id = 42"));
        assertTrue(condition.contains("is_deleted = 0"));
        assertTrue(condition.contains("id IN (10,11)"));
        String changes = setSql(wrapper);
        assertTrue(changes.contains("is_deleted=1"));
        assertTrue(changes.contains("is_pinned=0"));
        assertTrue(changes.contains("pinned_sort=0"));
        assertTrue(changes.contains("update_user=42"));
        f.assertLockFirst();
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void setPinned_非法固定状态不写入(Kind kind) {
        Fixture f = new Fixture(kind);
        f.owned = f.entity(10L, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> f.call("setPinned", USER, 10L, 2));
        assertTrue(f.updates().isEmpty());
        f.assertLockFirst();
    }

    private static void assertPin(Object entity, int pin, int sort) {
        assertEquals(pin, field(entity, "isPinned"));
        assertEquals(sort, field(entity, "pinnedSort"));
    }

    private static Object field(Object entity, String name) {
        return ReflectionTestUtils.getField(entity, name);
    }

    private static void assertScope(AbstractWrapper<?, ?, ?> wrapper, long id) {
        String condition = sql(wrapper);
        assertTrue(condition.contains("id = " + id), condition);
        assertTrue(condition.contains("user_id = 42"), condition);
        assertTrue(condition.contains("is_deleted = 0"), condition);
    }

    private static String sql(AbstractWrapper<?, ?, ?> wrapper) {
        return bind(wrapper.getSqlSegment(), wrapper);
    }

    private static String setSql(LambdaUpdateWrapper<?> wrapper) {
        return bind(wrapper.getSqlSet(), wrapper);
    }

    private static String bind(String sql, AbstractWrapper<?, ?, ?> wrapper) {
        return Pattern.compile("#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}")
                .matcher(sql).replaceAll(match -> Matcher.quoteReplacement(
                        String.valueOf(wrapper.getParamNameValuePairs().get(match.group(1)))));
    }

    enum Kind {
        GOAL(GoalEntity.class, GoalServiceImpl.class, IGoalMapper.class),
        ANNIVERSARY(AnniversaryRecordEntity.class, AnniversaryRecordServiceImpl.class, IAnniversaryRecordMapper.class);

        final Class<?> entity;
        final Class<?> service;
        final Class<?> mapper;

        Kind(Class<?> entity, Class<?> service, Class<?> mapper) {
            this.entity = entity;
            this.service = service;
            this.mapper = mapper;
        }
    }

    private record Call(String name, Object[] args) { }

    /** 只提供 Mapper 返回值和捕获调用，不模拟生产业务逻辑。 */
    private static final class Fixture {
        final Kind kind;
        final Object service;
        final List<Call> calls = new ArrayList<>();
        Object owned;
        List<Object> pinned = List.of();

        Fixture(Kind kind) {
            this.kind = kind;
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),
                    kind.entity.getName()), kind.entity);
            service = instantiate(kind.service);
            Object mapper = mock(kind.mapper, invocation -> {
                String name = invocation.getMethod().getName();
                calls.add(new Call(name, invocation.getArguments()));
                return switch (name) {
                    case "lockPinOwner" -> USER;
                    case "selectOne" -> owned;
                    case "selectList" -> pinned;
                    case "insert", "update" -> 1;
                    default -> Answers.RETURNS_DEFAULTS.answer(invocation);
                };
            });
            ReflectionTestUtils.setField(service, "baseMapper", mapper);
        }

        Object entity(Long id, Integer pin, Integer sort) {
            Object entity = instantiate(kind.entity);
            ReflectionTestUtils.setField(entity, "id", id);
            ReflectionTestUtils.setField(entity, "userId", USER);
            ReflectionTestUtils.setField(entity, "isPinned", pin);
            ReflectionTestUtils.setField(entity, "pinnedSort", sort);
            return entity;
        }

        Object call(String method, Object... args) {
            return ReflectionTestUtils.invokeMethod(service, method, args);
        }

        List<String> names() {
            return calls.stream().map(Call::name).toList();
        }

        List<Call> updates() {
            return calls.stream().filter(call -> call.name().equals("update")).toList();
        }

        void assertLockFirst() {
            assertEquals("lockPinOwner", calls.getFirst().name());
            assertEquals(USER, calls.getFirst().args()[0]);
            assertEquals(1, calls.stream().filter(call -> call.name().equals("lockPinOwner")).count());
        }

        void assertOwnedScopes(long id) {
            for (Call call : calls) {
                if (call.name().equals("selectOne")) {
                    assertScope((AbstractWrapper<?, ?, ?>) call.args()[0], id);
                } else if (call.name().equals("update")) {
                    assertScope((AbstractWrapper<?, ?, ?>) call.args()[1], id);
                }
            }
        }

        void assertPinnedQuery() {
            Call query = calls.stream().filter(call -> call.name().equals("selectList")).findFirst().orElseThrow();
            String condition = sql((AbstractWrapper<?, ?, ?>) query.args()[0]);
            assertTrue(condition.contains("user_id = 42"), condition);
            assertTrue(condition.contains("is_deleted = 0"), condition);
            assertTrue(condition.contains("is_pinned = 1"), condition);
            assertTrue(condition.contains("ORDER BY pinned_sort ASC,id DESC"), condition);
        }

        private static Object instantiate(Class<?> type) {
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
    }
}
