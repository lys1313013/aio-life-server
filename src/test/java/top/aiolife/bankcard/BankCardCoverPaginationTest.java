package top.aiolife.bankcard;

import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import top.aiolife.bankcard.mapper.BankCardCoverTemplateMapper;
import top.aiolife.bankcard.pojo.query.BankCardCoverQuery;
import top.aiolife.bankcard.pojo.vo.BankCardCoverTemplateVO;
import top.aiolife.bankcard.service.BankCardCoverTemplateService;
import top.aiolife.core.util.MinioUtil;
import static org.junit.jupiter.api.Assertions.*;

/** 使用真实 Mapper SQL 验证分页边界和服务端筛选，不连接开发数据库。 */
class BankCardCoverPaginationTest {
    JdbcTemplate jdbc;
    BankCardCoverTemplateService service;
    @BeforeEach void setup() throws Exception {
        var ds=new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE bank_card_cover_template(id BIGINT PRIMARY KEY,name VARCHAR(100),bank_id BIGINT,card_type VARCHAR(10),source_url VARCHAR(1000),is_enabled INT,sort_order INT,is_deleted INT)");
        jdbc.execute("CREATE TABLE sys_dict_data(dict_code BIGINT PRIMARY KEY,dict_label VARCHAR(100))");
        jdbc.execute("INSERT INTO sys_dict_data VALUES(20,'测试银行')");
        jdbc.execute("CREATE TABLE file(id VARCHAR(32),biz_id BIGINT,biz_type VARCHAR(50),is_deleted INT)");
        jdbc.execute("CREATE TABLE bank_card(cover_template_id BIGINT,is_deleted INT)");
        var config=new MybatisConfiguration();config.addMapper(BankCardCoverTemplateMapper.class);
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(config);
        var mapper=new SqlSessionTemplate(factory.getObject()).getMapper(BankCardCoverTemplateMapper.class);
        service=new BankCardCoverTemplateService(mapper,null,null,null,null,new MinioUtil());
    }
    @Test void 公共卡面按页查询并在数据库筛选和排序() {
        for (int i=1; i<=28; i++) {
            jdbc.update("INSERT INTO bank_card_cover_template(id,name,bank_id,card_type,is_enabled,sort_order,is_deleted) VALUES(?,?,?,?,?,?,?)",
                    i,"卡面"+i,20,i%2==0?"credit":"debit",i%3==0?0:1,0,i==28?1:0);
        }
        var query=new BankCardCoverQuery();
        var first=service.page(query);
        assertEquals(27L,first.getTotal());
        assertEquals(24,first.getItems().size());
        assertEquals("1",first.getItems().getFirst().id());
        query.setPage(2);
        assertEquals(List.of("25","26","27"),service.page(query).getItems().stream().map(BankCardCoverTemplateVO::id).toList());
        query.setPage(3);
        assertTrue(service.page(query).getItems().isEmpty());
        query.setPage(1);query.setBankId(20L);query.setCardType("credit");query.setIsEnabled(0);query.setKeyword(" 测试银行 ");
        var filtered=service.page(query);
        assertEquals(4L,filtered.getTotal());
        assertEquals(List.of("6","12","18","24"),filtered.getItems().stream().map(BankCardCoverTemplateVO::id).toList());
        query.setKeyword("卡面12");
        assertEquals(1L,service.page(query).getTotal());
        query.setKeyword("%");
        assertEquals(0L,service.page(query).getTotal());
    }
}
