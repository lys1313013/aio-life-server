package top.aiolife.bankcard;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import top.aiolife.bankcard.mapper.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mybatis.spring.SqlSessionTemplate;
import top.aiolife.bankcard.pojo.req.*;
import top.aiolife.bankcard.pojo.vo.BankCardVO;
import top.aiolife.bankcard.service.*;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.mapper.IBVideoMapper;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.service.FilePreviewGuard;
import top.aiolife.sso.service.AccountStatusGuard;
import top.aiolife.sso.service.SecondaryLockGuard;

import static org.junit.jupiter.api.Assertions.*;

/** 独立内存数据库执行真实SQL和事务，不连接项目配置中的数据库。 */
class BankCardServiceTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    BankCardService service;
    BankCardDictionaryGuard guard;
    BankCardCrypto crypto;
    BankCardCoverTemplateService templateService;
    BankCardFileMapper fileMapper;
    BankCardMapper cardMapper;
    BankCardCoverTemplateMapper coverMapper;
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
        String templateDdl=Files.readString(Path.of("sql/1_init_table/2026-10-02_bank_card_cover_template.sql")).split("-- 兼容")[0]
                .replace("USE `aio_life`;", "").replace(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", "");
        for(String statement:templateDdl.split(";")) if(!statement.isBlank()) jdbc.execute(statement);
        crypto = new BankCardCrypto(Base64.getEncoder().encodeToString(new byte[32]));
        var config = new MybatisConfiguration();
        config.addMappers("top.aiolife.bankcard.mapper");
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(config);
        var session = new SqlSessionTemplate(factory.getObject());
        var cards = session.getMapper(BankCardMapper.class);
        var dictionaries = session.getMapper(BankCardDictionaryMapper.class);
        var covers = session.getMapper(BankCardCoverTemplateMapper.class);
        cardMapper = cards;
        coverMapper = covers;
        var files = session.getMapper(BankCardFileMapper.class);
        fileMapper = files;
        guard = new BankCardDictionaryGuard(dictionaries, cards, covers);
        var minio = new MinioUtil();
        ReflectionTestUtils.setField(minio, "serveBaseUrl", "https://example.test/api");
        templateService = new BankCardCoverTemplateService(covers, guard, dictionaries, cards, files, minio);
        service = new BankCardService(cards, crypto, guard, dictionaries,
                session.getMapper(BankCardTagMapper.class), session.getMapper(BankCardTagRelMapper.class),
                files, covers, templateService);
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
        String json=new ObjectMapper().findAndRegisterModules().writeValueAsString(service.list(1));
        assertFalse(json.contains(NUMBER));assertFalse(json.contains("ciphertext"));assertFalse(json.contains("fingerprint"));
        assertEquals("6222",card.getCardNoFirst4());
        assertTrue(json.contains("\"cardNoFirst4\":\"6222\""));
        assertTrue(json.contains("\"cardNoLast4\":\"1234\""));
        assertEquals("1234",card.getCardNoLast4());assertTrue(service.list(2).isEmpty());
    }
    @Test void testCardNumberDisplay_编辑保留或更换卡号时返回正确首尾四位() {
        var req=request();
        var card=create(1,req);
        long id=Long.parseLong(card.getId());
        req.setCardNo(null);
        req.setAlias("工资卡");
        var unchanged=tx.execute(s->service.save(1,id,req));
        assertEquals("6222",unchanged.getCardNoFirst4());
        assertEquals("1234",unchanged.getCardNoLast4());
        req.setCardNo("0012 3456 7890 5678 901");
        var changed=tx.execute(s->service.save(1,id,req));
        assertEquals("0012",changed.getCardNoFirst4());
        assertEquals("8901",changed.getCardNoLast4());
        assertEquals("0012",service.list(1).getFirst().getCardNoFirst4());
    }
    @Test void testOptionalNumber_空卡号可重复新增并在编辑后补填() {
        var req=request();
        req.setCardNo(null);
        var first=create(1,req);
        long id=Long.parseLong(first.getId());
        assertNull(first.getCardNoFirst4());
        assertNull(first.getCardNoLast4());
        for (String blank : List.of("", " \t ")) {
            req.setCardNo(blank);
            assertNotNull(create(1,req));
        }
        assertEquals(3,service.list(1).size());
        assertNull(jdbc.queryForObject("SELECT card_no_ciphertext FROM bank_card WHERE id=?",String.class,id));
        assertNull(jdbc.queryForObject("SELECT card_no_fingerprint FROM bank_card WHERE id=?",byte[].class,id));
        assertEquals("尚未填写卡号",assertThrows(IllegalArgumentException.class,()->service.reveal(1,id)).getMessage());
        assertThrows(IllegalArgumentException.class,()->service.reveal(2,id));
        req.setAlias("待补充卡号");
        var edited=tx.execute(s->service.save(1,id,req));
        assertEquals("待补充卡号",edited.getAlias());
        assertNull(edited.getCardNoLast4());
        req.setCardNo(NUMBER);
        var filled=tx.execute(s->service.save(1,id,req));
        assertEquals("6222",filled.getCardNoFirst4());
        assertEquals("1234",filled.getCardNoLast4());
        assertEquals(NUMBER,service.reveal(1,id));
        assertThrows(IllegalArgumentException.class,()->create(1,req));
        req.setCardNo("  ");
        tx.execute(s->service.save(1,id,req));
        assertEquals(NUMBER,service.reveal(1,id));
    }
    @Test void testOptionalNumber_填写时仍校验格式() {
        var req=request();
        for (String invalid : List.of("1234", "abcdefghijkl", "-", "12345678901234567890")) {
            req.setCardNo(invalid);
            assertThrows(IllegalArgumentException.class,()->create(1,req));
        }
        assertTrue(service.list(1).isEmpty());
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
        var lock=Mockito.mock(SecondaryLockGuard.class);
        var preview=new FilePreviewGuard(lock,fileMapper,
                Mockito.mock(AccountStatusGuard.class), Mockito.mock(IBVideoMapper.class));
        var file=new FileEntity();
        file.setBizType("bank_card_cover");file.setCreateUser(1L);file.setIsPublic(1);
        assertEquals(FilePreviewGuard.AccessDecision.UNAUTHORIZED,preview.check(file,null));
        assertEquals(FilePreviewGuard.AccessDecision.FORBIDDEN,preview.check(file,2L));
        assertEquals(FilePreviewGuard.AccessDecision.ALLOW,preview.check(file,1L));
        file.setBizId(123L);
        assertEquals(FilePreviewGuard.AccessDecision.FORBIDDEN,preview.check(file,1L));
        Mockito.verify(lock,Mockito.atLeastOnce()).checkMenus(1L,"/finance/bank-cards");
    }

    BankCardCoverTemplateService templates() { return templateService; }
    BankCardCoverTemplateReq templateReq(String fileId) {
        var req=new BankCardCoverTemplateReq();
        req.setName("测试公共卡面");req.setBankId(20L);req.setCardType("debit");req.setFileId(fileId);
        return req;
    }
    void templateFile(String id,long userId) {
        jdbc.update("INSERT INTO file(id,biz_type,create_user) VALUES(?,?,?)",id,BankCardCoverTemplateService.FILE_TYPE,userId);
    }
    long template() {
        templateFile(FILE_A,1);
        return Long.parseLong(tx.execute(s->templates().save(1,null,templateReq(FILE_A))).id());
    }
    @Test void 公共卡面可被不同用户引用且替换图片同步生效() {
        long id=template();var req=request();req.setCoverTemplateId(id);
        var first=create(1,req);var second=create(2,req);
        assertEquals(FILE_A,first.getCoverTemplateFileId());assertTrue(first.getCoverFileIds().isEmpty());
        assertEquals(2,templates().detail(id).usageCount());
        String firstUrl = "https://example.test/api/public/images/" + FILE_A + ".png";
        assertEquals(firstUrl, first.getCoverTemplatePublicUrl());
        assertEquals(firstUrl, templates().detail(id).publicUrl());
        assertEquals(firstUrl, templates().options(20,"debit").getFirst().publicUrl());
        templateFile(FILE_B,2);
        var edit=templateReq(FILE_B);edit.setName("新版卡面");edit.setSourceUrl("https://example.com/card");
        tx.execute(s->templates().save(2,id,edit));
        assertEquals(FILE_B,service.detail(1,Long.parseLong(first.getId())).getCoverTemplateFileId());
        assertEquals("https://example.test/api/public/images/" + FILE_B + ".png",
                service.detail(1,Long.parseLong(first.getId())).getCoverTemplatePublicUrl());
        assertEquals("新版卡面",service.detail(2,Long.parseLong(second.getId())).getCoverTemplateName());
        assertEquals("https://example.com/card",service.list(1).getFirst().getCoverSourceUrl());
        assertEquals(1,jdbc.queryForObject("SELECT is_deleted FROM file WHERE id=?",Integer.class,FILE_A));
    }
    @Test void 停用不允许新选用但既有引用可编辑并显示() {
        long id=template();var req=request();req.setCoverTemplateId(id);var card=create(1,req);
        tx.execute(s->templates().setEnabled(1,id,0));
        assertTrue(templates().options(20,"debit").isEmpty());
        assertThrows(IllegalArgumentException.class,()->create(2,req));
        req.setAlias("仍可编辑");
        assertEquals(FILE_A,tx.execute(s->service.save(1,Long.valueOf(card.getId()),req)).getCoverTemplateFileId());
        assertThrows(IllegalArgumentException.class,()->tx.executeWithoutResult(s->templates().delete(1,id)));
        req.setCoverTemplateId(null);
        tx.execute(s->service.save(1,Long.valueOf(card.getId()),req));
        tx.executeWithoutResult(s->templates().delete(1,id));
        assertThrows(IllegalArgumentException.class,()->templates().detail(id));
    }
    @Test void 拒绝错配自定义银行私人图片混用和已引用类型修改() {
        long id=template();var req=request();req.setCoverTemplateId(id);req.setCardType("credit");
        assertThrows(IllegalArgumentException.class,()->create(1,req));
        req.setCardType("debit");req.setBankId(null);req.setCustomBankName("测试银行");
        assertThrows(IllegalArgumentException.class,()->create(1,req));
        req.setBankId(20L);req.setCustomBankName(null);file(FILE_B,1);req.setCoverFileIds(List.of(FILE_B));
        assertThrows(IllegalArgumentException.class,()->create(1,req));
        req.setCoverFileIds(List.of());create(1,req);
        var edit=templateReq(FILE_A);edit.setCardType("credit");
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->templates().save(1,id,edit)));
        assertThrows(IllegalArgumentException.class,()->tx.executeWithoutResult(s->guard.checkBankChange(20,null,true)));
    }
    @Test void 私人和公共卡面切换清理私人文件但删卡不删除模板() {
        long id=template();file(FILE_B,1);var req=request();req.setCoverFileIds(List.of(FILE_B));var card=create(1,req);
        req.setCoverFileIds(List.of());req.setCoverTemplateId(id);
        tx.execute(s->service.save(1,Long.valueOf(card.getId()),req));
        assertEquals(1,jdbc.queryForObject("SELECT is_deleted FROM file WHERE id=?",Integer.class,FILE_B));
        tx.executeWithoutResult(s->service.delete(1,Long.parseLong(card.getId())));
        assertEquals(0,templates().detail(id).usageCount());
        assertEquals(0,jdbc.queryForObject("SELECT is_deleted FROM file WHERE id=?",Integer.class,FILE_A));
    }
    @Test void 保存失败保留旧图并拒绝绑定其他模板和他人临时图片() {
        long id=template();templateFile(FILE_B,2);
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->templates().save(1,id,templateReq(FILE_B))));
        assertEquals(FILE_A,templates().detail(id).fileId());
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->templates().save(1,null,templateReq(FILE_A))));
        var req=templateReq(FILE_A);req.setSourceUrl("javascript:alert(1)");
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->templates().save(1,id,req)));
    }
    @Test void 银行停用后禁止新选用且既有银行卡可继续编辑() {
        long id=template();var req=request();req.setCoverTemplateId(id);var card=create(1,req);
        jdbc.update("UPDATE sys_dict_data SET status='1' WHERE dict_code=20");
        assertTrue(templates().options(20,"debit").isEmpty());
        assertThrows(IllegalArgumentException.class,()->create(2,req));
        assertEquals(FILE_A,tx.execute(s->service.save(1,Long.valueOf(card.getId()),req)).getCoverTemplateFileId());
    }
    @Test void 公共图片拒绝匿名和临时图读取停用只允许已有引用者() {
        long id=template();var req=request();req.setCoverTemplateId(id);create(1,req);
        var menu=Mockito.mock(SecondaryLockGuard.class);
        var preview=Mockito.spy(new FilePreviewGuard(menu,fileMapper,
                Mockito.mock(AccountStatusGuard.class), Mockito.mock(IBVideoMapper.class)));
        Mockito.doReturn(false).when(preview).isAdmin(ArgumentMatchers.anyLong());
        var file=new FileEntity();file.setBizType(BankCardCoverTemplateService.FILE_TYPE);file.setIsPublic(1);
        assertEquals(FilePreviewGuard.AccessDecision.UNAUTHORIZED,preview.check(file,null));
        assertEquals(FilePreviewGuard.AccessDecision.FORBIDDEN,preview.check(file,2L));
        file.setBizId(id);
        assertEquals(FilePreviewGuard.AccessDecision.ALLOW,preview.check(file,2L));
        tx.execute(s->templates().setEnabled(1,id,0));
        assertEquals(FilePreviewGuard.AccessDecision.FORBIDDEN,preview.check(file,2L));
        assertEquals(FilePreviewGuard.AccessDecision.ALLOW,preview.check(file,1L));
    }
    @Test void 并发删除与选用不会产生悬空引用() throws Exception {
        long id=template();var req=request();req.setCoverTemplateId(id);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var selected=pool.submit(()->{start.await();try{create(1,req);return true;}catch(IllegalArgumentException e){return false;}});
            var deleted=pool.submit(()->{start.await();try{tx.executeWithoutResult(s->templates().delete(1,id));return true;}catch(IllegalArgumentException e){return false;}});
            start.countDown();assertNotEquals(selected.get(10,TimeUnit.SECONDS),deleted.get(10,TimeUnit.SECONDS));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM bank_card c JOIN bank_card_cover_template t ON c.cover_template_id=t.id WHERE c.is_deleted=0 AND t.is_deleted=1",Integer.class));
        }
    }

    @Test void MyBatis更新会清空可选字段并保留创建审计信息() {
        var req = request();
        req.setAlias("旧别名"); req.setCardName("旧卡名"); req.setBranchName("旧支行");
        req.setRemark("旧备注"); req.setCoverColor("#abcdef");
        req.setOpenedDate(LocalDate.of(2020, 1, 1));
        req.setExpiryMonth(LocalDate.of(2030, 1, 1));
        long id = Long.parseLong(create(1, req).getId());
        var createdAt = jdbc.queryForObject("SELECT create_time FROM bank_card WHERE id=?", LocalDateTime.class, id);
        req.setAlias(null); req.setCardName(null); req.setBranchName(null); req.setRemark(null);
        req.setCoverColor(null); req.setOpenedDate(null); req.setExpiryMonth(null); req.setCardNo(null);
        var edited = tx.execute(status -> service.save(1, id, req));
        assertNull(edited.getAlias()); assertNull(edited.getCardName()); assertNull(edited.getBranchName());
        assertNull(edited.getRemark()); assertNull(edited.getCoverColor());
        assertNull(edited.getOpenedDate()); assertNull(edited.getExpiryMonth());
        assertEquals(NUMBER, service.reveal(1, id));
        assertEquals(createdAt, jdbc.queryForObject("SELECT create_time FROM bank_card WHERE id=?", LocalDateTime.class, id));
        assertEquals(1L, jdbc.queryForObject("SELECT create_user FROM bank_card WHERE id=?", Long.class, id));
    }

    @Test void 公共卡面出处与标签颜色支持清空() {
        long id = template();
        var req = templateReq(FILE_A); req.setSourceUrl("https://example.com/card");
        tx.execute(status -> templates().save(1, id, req));
        req.setSourceUrl(null);
        assertNull(tx.execute(status -> templates().save(1, id, req)).sourceUrl());
        var tag = tx.execute(status -> service.saveTag(1, null, new BankCardTagReq("颜色", "#abcdef", "0")));
        tx.execute(status -> service.saveTag(1, Long.valueOf(tag.id()), new BankCardTagReq("颜色", null, "0")));
        assertNull(service.tags(1).getFirst().color());
    }

    @Test void 锁查询在同一事务中也必须重新读取数据库() {
        long templateId = template();
        long cardId = Long.parseLong(create(1, request()).getId());
        tx.executeWithoutResult(status -> {
            assertEquals("测试公共卡面", coverMapper.lockById(templateId).getName());
            // 直接修改夹具不会触发 MyBatis 缓存清理，用于验证锁查询自身强制访问数据库。
            jdbc.update("UPDATE bank_card_cover_template SET name='新名称' WHERE id=?", templateId);
            assertEquals("新名称", coverMapper.lockById(templateId).getName());
            assertTrue(cardMapper.lockTemplateReferences(templateId).isEmpty());
            jdbc.update("UPDATE bank_card SET cover_template_id=? WHERE id=?", templateId, cardId);
            assertEquals(List.of(cardId), cardMapper.lockTemplateReferences(templateId));
        });
    }

}
