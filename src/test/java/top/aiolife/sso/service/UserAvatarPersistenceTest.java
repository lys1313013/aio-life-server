package top.aiolife.sso.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.mapper.IAttachmentMapper;
import top.aiolife.record.service.IMailService;
import top.aiolife.core.redis.RedisUtil;
import top.aiolife.sso.mapper.*;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.service.impl.UserServiceImpl;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserAvatarPersistenceTest {
    private static final String ID = "a1b2c3d4-1234-4abc-8def-1234567890ab";
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private UserServiceImpl users;

    @BeforeEach void setup() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:avatar_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        top.aiolife.support.AttachmentTestFixtures.create(jdbc);
        jdbc.execute("""
            CREATE TABLE sys_user(policy_version VARCHAR(32), policy_accepted_at TIMESTAMP, erasure_requested_at TIMESTAMP, erasure_retry_at TIMESTAMP, id BIGINT PRIMARY KEY, username VARCHAR(255), password VARCHAR(255),
              secondary_password VARCHAR(255), nickname VARCHAR(255),
              email VARCHAR(255), phone_country_code VARCHAR(32), phone VARCHAR(64),
              phone_verified_at TIMESTAMP, wechat_openid VARCHAR(255), wechat_unionid VARCHAR(255), role VARCHAR(255),
              introduction VARCHAR(255), is_deleted INT DEFAULT 0, last_active_at TIMESTAMP,
              create_user BIGINT, update_user BIGINT, create_time TIMESTAMP, update_time TIMESTAMP)
            """);
        jdbc.update("INSERT INTO sys_user(id,nickname) VALUES(42,'fixture')");
        jdbc.update("INSERT INTO attachment(id,file_key,user_id,create_user,biz_type,is_public) VALUES(1234567890123456789,?,42,42,'avatar',0)", ID);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.addMapper(UserMapper.class); config.addMapper(IAttachmentMapper.class);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(config);
        var session = new SqlSessionTemplate(factory.getObject());
        var minio = new MinioUtil(); ReflectionTestUtils.setField(minio,"serveBaseUrl","https://example.test/api");
        users = new UserServiceImpl(session.getMapper(UserMapper.class), mock(ApiKeyMapper.class), mock(LoginSessionService.class),
            mock(LoginLogMapper.class), mock(IMailService.class), mock(RedisUtil.class), mock(UserSecondaryLockMenuMapper.class),
            mock(SecondaryLockMenuResolver.class), new UserAvatarFileService(session.getMapper(IAttachmentMapper.class), minio,org.mockito.Mockito.mock(top.aiolife.record.service.VideoCoverStorage.class)));
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    }
    private UserEntity update() { var e = new UserEntity(); e.setId(42L); return e; }
    private String saved() { return jdbc.queryForObject("SELECT (SELECT file_key FROM attachment WHERE biz_type='avatar' AND biz_id=42)",String.class); }

    @Test void 仅更新头像也能保存且两个查询接口返回ID与URL() {
        tx.executeWithoutResult(s -> users.updateUser(update(),ID,true));
        assertEquals(ID,saved());
        assertEquals(ID,users.getUserInfo(42L).getAvatarFileId());
        assertEquals("https://example.test/api/f/"+"a".repeat(64)+".png",users.getUserInfo(42L).getAvatarUrl());
        assertEquals(ID,users.getUserBasicInfo(42L).getAvatarFileId());
        assertEquals("https://example.test/api/f/"+"a".repeat(64)+".png",users.getUserBasicInfo(42L).getAvatarUrl());
    }
    @Test void 缺省不变显式null解除关联且不删除文件() {
        tx.executeWithoutResult(s -> users.updateUser(update(),ID,true));
        tx.executeWithoutResult(s -> users.updateUser(update(),null,false)); assertEquals(ID,saved());
        tx.executeWithoutResult(s -> users.updateUser(update(),null,true)); assertNull(saved());
        assertNull(users.getUserInfo(42L).getAvatarUrl());
        assertEquals(1,jdbc.update("DELETE FROM attachment WHERE file_key=?",ID));
    }
    @Test void 他人文件拒绝绑定且其他资料不被部分更新() {
        jdbc.update("UPDATE attachment SET user_id=99 WHERE file_key=?",ID);
        var e=update();e.setNickname("must-not-save");
        assertThrows(IllegalArgumentException.class, () -> tx.executeWithoutResult(s -> users.updateUser(e,ID,true)));
        assertNull(saved()); assertEquals("fixture",jdbc.queryForObject("SELECT nickname FROM sys_user WHERE id=42",String.class));
    }
}
