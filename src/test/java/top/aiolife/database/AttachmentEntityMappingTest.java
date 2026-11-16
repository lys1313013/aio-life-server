package top.aiolife.database;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.ResolvableType;
import top.aiolife.core.mybatis.NumericBooleanTypeHandler;
import top.aiolife.support.AttachmentDatabaseFixture;
import static org.junit.jupiter.api.Assertions.*;

/** 当前迁移链上的全部物理业务表均通过实体Mapper全字段读取，不连接业务数据库。 */
class AttachmentEntityMappingTest extends AttachmentDatabaseFixture {
    @Test @SuppressWarnings({"rawtypes","unchecked"})
    void 四表切换后所有物理表均有实体并可查询全部字段() throws Exception {
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        config.getTypeHandlerRegistry().register(NumericBooleanTypeHandler.class);
        var types=new ArrayList<Class<?>>();
        Path classes=Path.of("target/classes");
        try(var paths=Files.walk(classes.resolve("top/aiolife"))) {
            for(var path:paths.filter(p->p.getFileName().toString().endsWith("Mapper.class")&&!p.getFileName().toString().contains("$")).toList()) {
                String name=classes.relativize(path).toString().replace('/','.').replaceAll("\\.class$","");
                Class<?> type=Class.forName(name);
                if(type.isInterface() && BaseMapper.class.isAssignableFrom(type)) { config.addMapper(type);types.add(type); }
            }
        }
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(manager.getDataSource());factory.setConfiguration(config);
        var all=new SqlSessionTemplate(factory.getObject());var bound=new HashSet<String>();
        for(var type:types) {
            var entity=ResolvableType.forClass(type).as(BaseMapper.class).getGeneric(0).resolve();
            assertNotNull(entity,type.getName());bound.add(TableInfoHelper.getTableInfo(entity).getTableName());
            ((BaseMapper)all.getMapper(type)).selectList(new QueryWrapper<>().last("LIMIT 1"));
        }
        var physical=jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=current_schema() AND table_type='BASE TABLE'",String.class);
        assertEquals(69,physical.size());
        assertTrue(bound.containsAll(physical),()->"缺失实体："+physical.stream().filter(x->!bound.contains(x)).toList());
    }
}
