package top.aiolife.bankcard;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.aiolife.bankcard.mapper.BankCardCoverTemplateMapper;
import top.aiolife.bankcard.mapper.BankCardMapper;
import top.aiolife.bankcard.pojo.req.BankCardMoveReq;
import top.aiolife.bankcard.service.BankCardOrderService;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 Mapper SQL、独立数据库与事务，验证排序持久化及范围隔离。 */
class BankCardOrderTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    BankCardOrderService service;

    @BeforeEach void setup() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        for (var table : List.of("bank_card", "bank_card_cover_template")) {
            jdbc.execute("CREATE TABLE " + table + "(id BIGINT PRIMARY KEY,user_id BIGINT,sort_order INT,is_deleted INT DEFAULT 0,update_user BIGINT,update_time DATETIME)");
            for (long id = 1; id <= 6; id++)
                jdbc.update("INSERT INTO " + table + "(id,user_id,sort_order,is_deleted) VALUES(?,?,0,?)", id, id == 5 ? 2 : 1, id == 6 ? 1 : 0);
        }
        var config = new MybatisConfiguration();
        config.addMapper(BankCardMapper.class);
        config.addMapper(BankCardCoverTemplateMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds); factory.setConfiguration(config);
        var session = new SqlSessionTemplate(factory.getObject());
        service = new BankCardOrderService(session.getMapper(BankCardMapper.class), session.getMapper(BankCardCoverTemplateMapper.class));
    }

    List<Long> covers() {
        return jdbc.queryForList("SELECT id FROM bank_card_cover_template WHERE is_deleted=0 ORDER BY sort_order,id", Long.class);
    }
    List<Long> cards() {
        return jdbc.queryForList("SELECT id FROM bank_card WHERE user_id=1 AND is_deleted=0 ORDER BY sort_order,id DESC", Long.class);
    }

    @Test void 卡面同序号跨筛选移动保留隐藏项顺序与未加载项() {
        var result = tx.execute(s -> service.moveCover(9, new BankCardMoveReq(4L, 1L, false)));
        assertEquals(List.of(4L, 1L, 2L, 3L, 5L), covers());
        assertEquals(5, result.size());
        assertEquals(9L, jdbc.queryForObject("SELECT update_user FROM bank_card_cover_template WHERE id=1", Long.class));
        assertNull(jdbc.queryForObject("SELECT update_user FROM bank_card_cover_template WHERE id=6", Long.class));
        tx.execute(s -> service.moveCover(9, new BankCardMoveReq(4L, 3L, true)));
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), covers());
    }

    @Test void 银行卡默认倒序支持首尾移动且不修改其他用户或已删除记录() {
        assertEquals(List.of(4L, 3L, 2L, 1L), cards());
        tx.execute(s -> service.moveCard(1, new BankCardMoveReq(1L, 4L, false)));
        assertEquals(List.of(1L, 4L, 3L, 2L), cards());
        tx.execute(s -> service.moveCard(1, new BankCardMoveReq(1L, 2L, true)));
        assertEquals(List.of(4L, 3L, 2L, 1L), cards());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM bank_card WHERE id IN (5,6) AND update_user IS NOT NULL", Integer.class));
    }

    @Test void 拒绝跨用户移动和跨用户目标且不产生部分更新() {
        for (var req : List.of(new BankCardMoveReq(5L, 1L, false), new BankCardMoveReq(1L, 5L, true), new BankCardMoveReq(6L, 1L, false)))
            assertThrows(IllegalArgumentException.class, () -> tx.execute(s -> service.moveCard(1, req)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM bank_card WHERE update_user IS NOT NULL", Integer.class));
    }

    @Test void 目标删除拒绝移动且原序号保留() {
        assertThrows(IllegalArgumentException.class, () -> tx.execute(s -> service.moveCover(9, new BankCardMoveReq(1L, 6L, false))));
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), covers());
    }

    @Test void 排序中途失败回滚已更新的行() {
        jdbc.execute("ALTER TABLE bank_card_cover_template ADD CONSTRAINT reject_order CHECK(id<>2 OR sort_order=0)");
        assertThrows(RuntimeException.class, () -> tx.execute(s -> service.moveCover(9, new BankCardMoveReq(4L, 1L, false))));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM bank_card_cover_template WHERE update_user IS NOT NULL", Integer.class));
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), covers());
    }

    @Test void 重复请求幂等且自身移动不写库() {
        var req = new BankCardMoveReq(3L, 1L, false);
        tx.execute(s -> service.moveCover(9, req));
        var previous = covers();
        tx.execute(s -> service.moveCover(9, req));
        assertEquals(previous, covers());
        tx.execute(s -> service.moveCard(1, new BankCardMoveReq(1L, 1L, false)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM bank_card WHERE update_user IS NOT NULL", Integer.class));
    }
}
