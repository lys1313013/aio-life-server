package top.aiolife.core.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import top.aiolife.record.pojo.entity.ExerciseRecordEntity;
import top.aiolife.record.pojo.entity.MovieEntity;

import static org.junit.jupiter.api.Assertions.*;

/** 使用真实实体与 MyBatis 主键映射验证身份契约，不依赖数据库。 */
class EntityEqualityTest {

    static Stream<Class<?>> tableEntities() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TableName.class));
        var types = new ArrayList<Class<?>>();
        for (var definition : scanner.findCandidateComponents("top.aiolife")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            if (type.getPackageName().endsWith(".pojo.entity")) {
                types.add(type);
            }
        }
        assertTrue(types.size() >= 65, "必须扫描全部 PostgreSQL 实体");
        return types.stream().sorted(Comparator.comparing(Class::getName));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tableEntities")
    void 所有关系实体按实际主键比较且分配主键不破坏哈希集合(Class<?> type) throws Exception {
        assertTrue(IdentityEntity.class.isAssignableFrom(type), type.getName());
        for (var method : List.of(type.getMethod("equals", Object.class), type.getMethod("hashCode"))) {
            assertEquals(IdentityEntity.class, method.getDeclaringClass());
            assertTrue(Modifier.isFinal(method.getModifiers()));
        }

        var table = TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), type.getName()), type);
        assertNotNull(table.getKeyProperty(), "实体必须有主键映射");
        assertFalse(table.getFieldList().stream().anyMatch(field -> field.getProperty().equals("identityKey")));
        var first = type.getDeclaredConstructor().newInstance();
        var second = type.getDeclaredConstructor().newInstance();
        var third = type.getDeclaredConstructor().newInstance();

        assertEquals(first, first);
        assertNotEquals(first, null);
        assertNotEquals(first, new Object());
        assertNotEquals(first, second, "无主键的两个对象不能相等");
        var set = new HashSet<>();
        set.add(first);
        set.add(second);
        assertEquals(2, set.size());
        set.remove(second);
        var map = new HashMap<>();
        map.put(first, "record");
        int hashBeforeInsert = first.hashCode();

        var firstBean = new BeanWrapperImpl(first);
        Object key = key(table.getKeyType(), 1);
        firstBean.setPropertyValue(table.getKeyProperty(), key);
        assertNotEquals(first, second, "有主键对象不能等于无主键对象");
        assertNotEquals(second, first);
        new BeanWrapperImpl(second).setPropertyValue(table.getKeyProperty(), key);
        new BeanWrapperImpl(third).setPropertyValue(table.getKeyProperty(), key);
        assertEquals(first, second);
        assertEquals(second, first);
        assertEquals(second, third);
        assertEquals(first, third);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(hashBeforeInsert, first.hashCode());
        assertTrue(set.contains(first));
        assertTrue(set.contains(second));
        assertFalse(set.add(second), "同一记录只能保留一个元素");
        assertEquals("record", map.get(second));

        new BeanWrapperImpl(third).setPropertyValue(table.getKeyProperty(), key(table.getKeyType(), 2));
        assertNotEquals(first, third, "业务字段相同但主键不同的记录不能相等");
        assertTrue(set.remove(second));
        assertTrue(set.isEmpty());
    }

    @Test
    void 更新业务审计和删除状态仍代表同一记录() {
        var original = new ExerciseRecordEntity();
        original.setId(7L);
        var updated = new ExerciseRecordEntity();
        updated.setId(7L);
        updated.setUserId(99L);
        updated.setExerciseCount(12);
        updated.setDescription("更新后的记录");
        updated.setExerciseDate(LocalDate.of(2026, 11, 10));
        updated.setCreateUser(1L);
        updated.setCreateTime(LocalDateTime.of(2026, 11, 9, 12, 0));
        updated.setUpdateUser(2L);
        updated.setUpdateTime(LocalDateTime.of(2026, 11, 10, 12, 0));
        updated.setIsDeleted(1);
        assertEquals(original, updated);
        assertEquals(original.hashCode(), updated.hashCode());

        var set = new HashSet<>(List.of(updated));
        updated.setExerciseCount(20);
        updated.fillUpdateCommonField(3L);
        assertTrue(set.contains(original));
    }

    @Test
    void 不同实体类型及继承类型即使主键相同也不相等() {
        var exercise = new ExerciseRecordEntity();
        exercise.setId(7L);
        var movie = new MovieEntity();
        movie.setId(7L);
        var subclass = new ExerciseSubtype();
        subclass.setId(7L);
        assertNotEquals(exercise, movie);
        assertNotEquals(movie, exercise);
        assertNotEquals(exercise, subclass);
        assertNotEquals(subclass, exercise);
        assertEquals(3, new HashSet<>(List.of(exercise, movie, subclass)).size());
    }

    private static Object key(Class<?> type, int value) {
        if (type == Long.class) return (long) value;
        if (type == String.class) return "record-" + value;
        if (type == LocalDate.class) return LocalDate.of(2026, 11, value);
        throw new AssertionError("未覆盖的主键类型：" + type.getName());
    }

    private static class ExerciseSubtype extends ExerciseRecordEntity {
    }
}
