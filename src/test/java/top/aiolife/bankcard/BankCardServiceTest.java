package top.aiolife.bankcard;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.aiolife.bankcard.mapper.BankCardRepository;
import top.aiolife.bankcard.pojo.req.*;
import top.aiolife.bankcard.pojo.vo.BankCardVO;
import top.aiolife.bankcard.service.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** 独立内存数据库执行真实SQL和事务，不连接项目配置中的数据库。 */
class BankCardServiceTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    BankCardService service;
    BankCardDictionaryGuard guard;
    BankCardCrypto crypto;
    static final String NUMBER = "6222000000001234";
    static final String FILE_A = "a".repeat(32);
    static final String FILE_B = "b".repeat(32);
    @BeforeEach void setup() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds); tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("CREATE TABLE user(id BIGINT PRIMARY KEY,is_deleted INT DEFAULT 0)");
        jdbc.execute("INSERT INTO user(id) VALUES(1),(2)");
        jdbc.execute("CREATE TABLE sys_dict_type(dict_id BIGINT PRIMARY KEY,dict_type VARCHAR(100),status CHAR(1),is_deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE sys_dict_data(dict_code BIGINT PRIMARY KEY,dict_id BIGINT,dict_label VARCHAR(100),dict_value VARCHAR(100),dict_sort INT,status CHAR(1),is_deleted INT DEFAULT 0)");
        jdbc.execute("INSERT INTO sys_dict_type VALUES(10,'bank','0',0),(11,'other','0',0)");
        jdbc.execute("INSERT INTO sys_dict_data VALUES(20,10,'测试银行','TEST',0,'0',0),(21,11,'其他字典','OTHER',0,'0',0)");
        jdbc.execute("CREATE TABLE user_dict_data(id BIGINT PRIMARY KEY,user_id BIGINT,dict_type VARCHAR(100),dict_label VARCHAR(100),dict_value VARCHAR(100),color VARCHAR(20),dict_sort INT,status CHAR(1),is_default CHAR(1),is_readonly CHAR(1),is_deleted INT DEFAULT 0,create_user BIGINT,update_user BIGINT,create_time DATETIME,update_time DATETIME)");
        jdbc.execute("CREATE TABLE file(id VARCHAR(32) PRIMARY KEY,biz_type VARCHAR(50),biz_id BIGINT,create_user BIGINT,update_user BIGINT,is_deleted INT DEFAULT 0,is_public INT DEFAULT 0,update_time DATETIME)");
        String ddl=Files.readString(Path.of("sql/1_init_table/2026-09-26_add_bank_card.sql"))
                .replaceAll("(?m)^--.*$", "").replace("USE `aio_life`;", "")
                .replaceAll("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", "");
        for(String statement:ddl.split(";")) if(!statement.isBlank()) jdbc.execute(statement);
        crypto = new BankCardCrypto(Base64.getEncoder().encodeToString(new byte[32]));
        guard = new BankCardDictionaryGuard(jdbc);
        service = new BankCardService(new BankCardRepository(jdbc),crypto,guard,jdbc);
    }
    BankCardReq request() {
        var req=new BankCardReq(); req.setBankId(20L);req.setCardType("debit");req.setStatus("normal");req.setCardNo(NUMBER);return req;
    }
    BankCardVO create(long userId,BankCardReq req) { return tx.execute(s->service.save(userId,null,req)); }
    void file(String id,long userId) { jdbc.update("INSERT INTO file(id,biz_type,create_user) VALUES(?,?,?)",id,"bank_card_cover",userId); }
    @Test void cardNumberIsEncryptedScopedAndNeverReturnedByList() throws Exception {
        var card=create(1,request());long id=Long.parseLong(card.getId());
        String encrypted=jdbc.queryForObject("SELECT card_no_ciphertext FROM bank_card WHERE id=?",String.class,id);
        assertFalse(encrypted.contains(NUMBER));assertEquals(NUMBER,service.reveal(1,id));
        assertThrows(IllegalArgumentException.class,()->service.reveal(2,id));
        String json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(service.list(1));
        assertFalse(json.contains(NUMBER));assertFalse(json.contains("ciphertext"));assertFalse(json.contains("fingerprint"));
        assertEquals("1234",card.getCardNoLast4());assertTrue(service.list(2).isEmpty());
    }
    @Test void customBankCanBeCreatedRenamedAndSwitchedWithoutChangingSystemDictionary() {
        var req=request(); req.setBankId(null);req.setCustomBankName("  自定义地方银行  ");
        var card=create(1,req);long id=Long.parseLong(card.getId());
        assertNull(card.getBankId());assertEquals("自定义地方银行",card.getBankName());
        assertEquals("自定义地方银行",card.getCustomBankName());assertEquals("",card.getBankCode());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict_data",Integer.class));
        assertTrue(service.list(2).isEmpty());
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->service.save(2,id,req)));
        req.setCardNo(null);req.setCustomBankName("新银行名称");
        assertEquals("新银行名称",tx.execute(s->service.save(1,id,req)).getBankName());
        req.setCustomBankName(null);req.setBankId(20L);
        var linked=tx.execute(s->service.save(1,id,req));
        assertEquals("20",linked.getBankId());assertNull(linked.getCustomBankName());assertEquals("测试银行",linked.getBankName());
        req.setBankId(null);req.setCustomBankName("我的银行");
        assertNull(tx.execute(s->service.save(1,id,req)).getBankId());
        assertNull(jdbc.queryForObject("SELECT bank_id FROM bank_card WHERE id=?",Long.class,id));
        jdbc.update("UPDATE sys_dict_data SET status='1' WHERE dict_code=20");
        req.setBankId(20L);req.setCustomBankName(null);
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->service.save(1,id,req)));
        assertEquals("我的银行",service.detail(1,id).getBankName());
    }
    @Test void customBankMustHaveExactlyOneSourceAndValidName() {
        var req=request();req.setBankId(null);
        assertThrows(IllegalArgumentException.class,()->create(1,req));
        req.setCustomBankName(" \t　 ");assertThrows(IllegalArgumentException.class,()->create(1,req));
        req.setCustomBankName("银行");req.setBankId(20L);
        assertThrows(IllegalArgumentException.class,()->create(1,req));
        req.setBankId(null);req.setCustomBankName("行".repeat(101));
        assertThrows(IllegalArgumentException.class,()->create(1,req));
        req.setCustomBankName("行".repeat(100));assertNotNull(create(1,req));
    }
    @Test void duplicateCardsAndSoftDeleteRecreation() {
        var first=create(1,request());
        assertThrows(IllegalArgumentException.class,()->create(1,request()));
        assertNotNull(create(2,request()));
        tx.executeWithoutResult(s->service.delete(1,Long.parseLong(first.getId())));
        var second=create(1,request());tx.executeWithoutResult(s->service.delete(1,Long.parseLong(second.getId())));
        assertNotNull(create(1,request()));
    }
    @Test void nullableCreditFieldsAreActuallyCleared() {
        var req=request(); req.setCardType("credit");req.setCreditLimit(new BigDecimal("12345.67"));req.setStatementDay(10);req.setRepaymentDay(28);
        var card=create(1,req);req.setCardNo(null);req.setCardType("debit");req.setCreditLimit(null);req.setStatementDay(null);req.setRepaymentDay(null);
        var edited=tx.execute(s->service.save(1,Long.parseLong(card.getId()),req));
        assertNull(edited.getCreditLimit());assertNull(edited.getStatementDay());assertEquals(NUMBER,service.reveal(1,Long.parseLong(card.getId())));
    }
    @Test void replacingCoverIsAtomicAndDoesNotStealFiles() {
        file(FILE_A,1);file(FILE_B,2);var req=request();req.setCoverFileIds(List.of(FILE_A));var card=create(1,req);long id=Long.parseLong(card.getId());
        req.setCardNo(null);req.setAlias("不得提交");req.setCoverFileIds(List.of(FILE_B));
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->service.save(1,id,req)));
        assertNull(service.detail(1,id).getAlias());assertEquals(List.of(FILE_A),service.detail(1,id).getCoverFileIds());
        jdbc.update("UPDATE file SET create_user=1 WHERE id=?",FILE_B);
        var otherReq=request();otherReq.setCardNo("6222000000005678");otherReq.setCoverFileIds(List.of(FILE_B));create(1,otherReq);
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->service.save(1,id,req)));
        req.setCoverFileIds(List.of());tx.execute(s->service.save(1,id,req));
        assertTrue(service.detail(1,id).getCoverFileIds().isEmpty());assertEquals(1,jdbc.queryForObject("SELECT is_deleted FROM file WHERE id=?",Integer.class,FILE_A));
    }
    @Test void tagsArePrivateRenamableAndRemovedWithRelations() {
        var tag=tx.execute(s->service.saveTag(1,null,new BankCardTagReq("工资卡","#64748b","0")));
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->service.saveTag(1,null,new BankCardTagReq(" 工资卡 ",null,"0"))));
        var req=request();req.setTagIds(List.of(Long.valueOf(tag.id())));
        assertThrows(IllegalArgumentException.class,()->create(2,req));
        var card=create(1,req);
        tx.execute(s->service.saveTag(1,Long.valueOf(tag.id()),new BankCardTagReq("日常",null,"1")));
        assertEquals("日常",service.list(1).getFirst().getTags().getFirst().name());
        req.setCardNo(null);tx.execute(s->service.save(1,Long.valueOf(card.getId()),req));
        tx.executeWithoutResult(s->service.deleteTag(1,Long.parseLong(tag.id())));
        assertTrue(service.list(1).getFirst().getTags().isEmpty());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM bank_card_tag_rel WHERE is_deleted=0",Integer.class));
    }
    @Test void bankDictionaryTypeAvailabilityAndDeletionGuard() {
        var wrong=request();wrong.setBankId(21L);assertThrows(IllegalArgumentException.class,()->create(1,wrong));
        var card=create(1,request());jdbc.update("UPDATE sys_dict_data SET status='1' WHERE dict_code=20");
        assertThrows(IllegalArgumentException.class,()->create(2,request()));
        var edit=request();edit.setCardNo(null);edit.setAlias("仍可编辑");
        assertEquals("仍可编辑",tx.execute(s->service.save(1,Long.valueOf(card.getId()),edit)).getAlias());
        assertThrows(IllegalArgumentException.class,()->tx.executeWithoutResult(s->guard.checkBankChange(20,null,true)));
        assertThrows(IllegalArgumentException.class,()->tx.executeWithoutResult(s->guard.checkTypeChange(10,"other",false)));
    }
    @Test void concurrentCreationCannotBypassDuplicateCheck() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Boolean> action=()->{start.await();try{create(1,request());return true;}catch(IllegalArgumentException expected){return false;}};
            var one=pool.submit(action);var two=pool.submit(action);start.countDown();
            assertNotEquals(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS));assertEquals(1,service.list(1).size());
        }
    }
    @Test void cryptoRejectsMissingKeyAndTampering() {
        var absent=new BankCardCrypto("");assertThrows(IllegalStateException.class,()->absent.encrypt(NUMBER,1,1));
        var a=crypto.encrypt(NUMBER,1,1);var b=crypto.encrypt(NUMBER,1,1);assertNotEquals(a,b);
        assertThrows(IllegalStateException.class,()->crypto.decrypt(a,2,1));assertThrows(IllegalStateException.class,()->crypto.decrypt(a,1,2));
        assertArrayEquals(crypto.fingerprint(NUMBER,1),crypto.fingerprint(NUMBER,1));assertFalse(Arrays.equals(crypto.fingerprint(NUMBER,1),crypto.fingerprint(NUMBER,2)));
        assertEquals(NUMBER,crypto.normalize("6222 0000-0000 1234"));
    }
    @Test void privateCoverGuardChecksOwnerAndDeletedBankCard() {
        var lock=org.mockito.Mockito.mock(top.aiolife.sso.service.SecondaryLockGuard.class);
        var preview=new top.aiolife.record.service.FilePreviewGuard(lock,jdbc);
        var file=new top.aiolife.record.pojo.entity.FileEntity();
        file.setBizType("bank_card_cover");file.setCreateUser(1L);file.setIsPublic(1);
        assertEquals(top.aiolife.record.service.FilePreviewGuard.AccessDecision.UNAUTHORIZED,preview.check(file,null));
        assertEquals(top.aiolife.record.service.FilePreviewGuard.AccessDecision.FORBIDDEN,preview.check(file,2L));
        assertEquals(top.aiolife.record.service.FilePreviewGuard.AccessDecision.ALLOW,preview.check(file,1L));
        file.setBizId(123L);
        assertEquals(top.aiolife.record.service.FilePreviewGuard.AccessDecision.FORBIDDEN,preview.check(file,1L));
        org.mockito.Mockito.verify(lock,org.mockito.Mockito.atLeastOnce()).checkMenus(1L,"/finance/bank-cards");
    }

}
