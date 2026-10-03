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
import top.aiolife.core.cache.SecondaryLockMenuCache;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.mapper.IFileMapper;
import top.aiolife.record.service.IMailService;
import top.aiolife.record.util.RedisUtil;
import top.aiolife.sso.mapper.*;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.service.impl.UserServiceImpl;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserAvatarPersistenceTest {
    private static final String ID = "0123456789abcdef0123456789abcdef";
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private UserServiceImpl users;

    @BeforeEach void setup() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:avatar_" + UUID.randomUUID() + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
            CREATE TABLE file(id VARCHAR(32) PRIMARY KEY, storage_object_id BIGINT, file_name VARCHAR(255),
              file_size BIGINT, file_type VARCHAR(100), hash_value VARCHAR(128), biz_type VARCHAR(50), biz_id BIGINT,
              is_public INT, is_deleted INT DEFAULT 0, create_user BIGINT, update_user BIGINT, create_time TIMESTAMP, update_time TIMESTAMP)
            """);
        jdbc.execute("""
            CREATE TABLE user(id BIGINT PRIMARY KEY, username VARCHAR(255), password VARCHAR(255), password_salt VARCHAR(255),
              secondary_password VARCHAR(255), secondary_password_salt VARCHAR(255), nickname VARCHAR(255),
              avatar_file_id VARCHAR(32), email VARCHAR(255), phone_country_code VARCHAR(32), phone VARCHAR(64),
              phone_verified_at TIMESTAMP, wechat_openid VARCHAR(255), wechat_unionid VARCHAR(255), role VARCHAR(255),
              introduction VARCHAR(255), is_deleted INT DEFAULT 0, last_active_at TIMESTAMP,
              create_user BIGINT, update_user BIGINT, create_time TIMESTAMP, update_time TIMESTAMP,
              FOREIGN KEY(avatar_file_id) REFERENCES file(id) ON DELETE RESTRICT)
            """);
        jdbc.update("INSERT INTO user(id,nickname) VALUES(42,'fixture')");
        jdbc.update("INSERT INTO file(id,create_user,biz_type,is_public,file_type) VALUES(?,42,'avatar',1,'image/png')", ID);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.addMapper(UserMapper.class); config.addMapper(IFileMapper.class);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(config);
        var session = new SqlSessionTemplate(factory.getObject());
        var minio = new MinioUtil(); ReflectionTestUtils.setField(minio,"serveBaseUrl","https://example.test/api");
        users = new UserServiceImpl(session.getMapper(UserMapper.class), mock(ApiKeyMapper.class), mock(LoginSessionService.class),
            mock(LoginLogMapper.class), mock(IMailService.class), mock(RedisUtil.class), mock(UserSecondaryLockMenuMapper.class),
            mock(SecondaryLockMenuCache.class), new UserAvatarFileService(session.getMapper(IFileMapper.class), minio));
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    }
    private UserEntity update(String id) { var e = new UserEntity(); e.setId(42L); e.setAvatarFileId(id); return e; }
    private String saved() { return jdbc.queryForObject("SELECT avatar_file_id FROM user WHERE id=42",String.class); }

    @Test void 仅更新头像也能保存且两个查询接口返回ID与URL() {
        tx.executeWithoutResult(s -> users.updateUser(update(ID),true));
        assertEquals(ID,saved());
        assertEquals(ID,users.getUserInfo(42L).getAvatarFileId());
        assertEquals("https://example.test/api/file/preview/"+ID,users.getUserInfo(42L).getAvatarUrl());
        assertEquals(ID,users.getUserBasicInfo(42L).getAvatarFileId());
        assertEquals("https://example.test/api/file/preview/"+ID,users.getUserBasicInfo(42L).getAvatarUrl());
    }
    @Test void 缺省不变显式null清除并允许后续删除文件() {
        tx.executeWithoutResult(s -> users.updateUser(update(ID),true));
        var other = update(null); other.setNickname("changed");
        tx.executeWithoutResult(s -> users.updateUser(other,false)); assertEquals(ID,saved());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM file WHERE id=?",ID));
        tx.executeWithoutResult(s -> users.updateUser(update(null),true)); assertNull(saved());
        assertNull(users.getUserInfo(42L).getAvatarUrl());
        assertEquals(1,jdbc.update("DELETE FROM file WHERE id=?",ID));
    }
    @Test void 他人文件拒绝绑定且其他资料不被部分更新() {
        jdbc.update("UPDATE file SET create_user=99 WHERE id=?",ID);
        var e=update(ID);e.setNickname("must-not-save");
        assertThrows(IllegalArgumentException.class, () -> tx.executeWithoutResult(s -> users.updateUser(e,true)));
        assertNull(saved()); assertEquals("fixture",jdbc.queryForObject("SELECT nickname FROM user WHERE id=42",String.class));
    }
}
