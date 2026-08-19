package top.aiolife.database;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.ResolvableType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.config.MybatisPlusConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 使用当前配置的真实 MySQL 数据库验证物理表、实体与 MyBatis-Plus Mapper 的映射关系。
 *
 * <p>测试只执行 SELECT，不插入、更新或删除任何数据。每个 Mapper 都通过未指定 select
 * 字段的 QueryWrapper 查询一条数据，以便 MyBatis-Plus 按实体映射生成全部持久化字段。</p>
 */
@ActiveProfiles("test")
@SpringBootTest(
        classes = PhysicalTableEntitySelectIntegrationTest.DatabaseMapperTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Transactional(readOnly = true)
class PhysicalTableEntitySelectIntegrationTest {

    private static final String PHYSICAL_TABLE_SQL = """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_type = 'BASE TABLE'
            ORDER BY table_name
            """;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 所有物理表均可按实体全部字段查询一条() {
        Set<String> physicalTables = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        jdbcTemplate.queryForList(PHYSICAL_TABLE_SQL, String.class)
                .stream()
                .map(PhysicalTableEntitySelectIntegrationTest::normalizeTableName)
                .forEach(physicalTables::add);
        assertFalse(physicalTables.isEmpty(), "当前数据库中没有物理表，请检查测试数据源配置");

        List<String> failures = new ArrayList<>();
        Map<String, List<MapperBinding>> bindingsByTable = discoverMapperBindings(failures);

        for (String table : physicalTables) {
            if (!isMigrationBackupTable(table) && !bindingsByTable.containsKey(table)) {
                failures.add("物理表 `" + table + "` 没有对应的 MyBatis-Plus 实体 Mapper；字段："
                        + describeColumns(table));
            }
        }

        for (Map.Entry<String, List<MapperBinding>> entry : bindingsByTable.entrySet()) {
            String table = entry.getKey();
            if (!physicalTables.contains(table)) {
                for (MapperBinding binding : entry.getValue()) {
                    failures.add(binding.description() + " 映射的物理表 `" + table + "` 不存在");
                }
                continue;
            }

            for (MapperBinding binding : entry.getValue()) {
                try {
                    selectOneWithAllEntityColumns(binding.mapper());
                } catch (Exception exception) {
                    failures.add(binding.description() + " 查询失败：" + rootMessage(exception));
                }
            }
        }

        if (!failures.isEmpty()) {
            fail("真实数据库实体全字段查询校验失败：\n- " + String.join("\n- ", failures));
        }
    }

    private Map<String, List<MapperBinding>> discoverMapperBindings(List<String> failures) {
        Map<String, List<MapperBinding>> result = new LinkedHashMap<>();
        String[] beanNames = applicationContext.getBeanNamesForType(BaseMapper.class);

        for (String beanName : beanNames) {
            BaseMapper<?> mapper = applicationContext.getBean(beanName, BaseMapper.class);
            Class<?> mapperType = resolveMapperType(beanName, mapper);
            Class<?> entityType = resolveEntityType(mapperType);
            if (entityType == null) {
                failures.add("Mapper Bean `" + beanName + "` 无法解析实体泛型");
                continue;
            }

            TableInfo tableInfo = TableInfoHelper.getTableInfo(entityType);
            if (tableInfo == null) {
                failures.add(mapperType.getName() + " 的实体 " + entityType.getName()
                        + " 没有 MyBatis-Plus 表映射信息");
                continue;
            }

            String tableName = normalizeTableName(tableInfo.getTableName());
            result.computeIfAbsent(tableName, ignored -> new ArrayList<>())
                    .add(new MapperBinding(mapper, mapperType, entityType));
        }
        return result;
    }

    private Class<?> resolveMapperType(String beanName, BaseMapper<?> mapper) {
        Class<?> beanType = applicationContext.getType(beanName);
        if (beanType != null && resolveEntityType(beanType) != null) {
            return beanType;
        }
        return Arrays.stream(mapper.getClass().getInterfaces())
                .filter(type -> resolveEntityType(type) != null)
                .findFirst()
                .orElse(mapper.getClass());
    }

    private static Class<?> resolveEntityType(Class<?> mapperType) {
        return ResolvableType.forClass(mapperType)
                .as(BaseMapper.class)
                .getGeneric(0)
                .resolve();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void selectOneWithAllEntityColumns(BaseMapper<?> mapper) {
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.last("LIMIT 1");
        ((BaseMapper) mapper).selectList(queryWrapper);
    }

    private static String normalizeTableName(String tableName) {
        String normalized = tableName.replace("`", "");
        int schemaSeparator = normalized.lastIndexOf('.');
        if (schemaSeparator >= 0) {
            normalized = normalized.substring(schemaSeparator + 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String describeColumns(String tableName) {
        return String.join(", ", jdbcTemplate.queryForList("""
                        SELECT CONCAT(column_name, ' ', column_type)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = ?
                        ORDER BY ordinal_position
                        """, String.class, tableName));
    }

    /** 历史迁移备份表不属于应用运行时模型，不为其维护业务实体。 */
    private static boolean isMigrationBackupTable(String tableName) {
        return tableName.contains("_bak_");
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    private record MapperBinding(BaseMapper<?> mapper, Class<?> mapperType, Class<?> entityType) {

        private String description() {
            return mapperType.getName() + " -> " + entityType.getName();
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(MybatisPlusConfig.class)
    static class DatabaseMapperTestApplication {
    }
}
