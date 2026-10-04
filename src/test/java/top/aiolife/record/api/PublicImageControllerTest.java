package top.aiolife.record.api;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import top.aiolife.config.MinioConfig;
import top.aiolife.config.SaTokenConfig;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.mapper.PublicImageMapper;
import top.aiolife.sso.interceptor.ApiKeyInterceptor;
import top.aiolife.sso.interceptor.SecondaryLockInterceptor;
import top.aiolife.sso.interceptor.UserLastActiveInterceptor;
import top.aiolife.sso.service.AccountStatusGuard;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static top.aiolife.support.ImageFixtures.image;

/** 真实 Mapper 和 MVC/生产拦截规则；仅对象存储模拟，不访问线上数据库或图片。 */
class PublicImageControllerTest {
    private static final String ID = "a".repeat(32), NEXT = "b".repeat(32);
    private JdbcTemplate jdbc;
    private PublicImageMapper mapper;
    private MinioUtil minio;
    private MockMvc mvc;
    private ApiKeyInterceptor keys;
    private SecondaryLockInterceptor locks;
    private UserLastActiveInterceptor activity;

    @BeforeEach void setup() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE file(id VARCHAR(32) PRIMARY KEY,file_name VARCHAR(255),file_type VARCHAR(100),biz_type VARCHAR(100),biz_id BIGINT,create_user BIGINT,is_deleted INT,is_public INT,storage_object_id BIGINT)");
        jdbc.execute("CREATE TABLE bank_card_cover_template(id BIGINT PRIMARY KEY,is_deleted INT,is_enabled INT)");
        jdbc.execute("CREATE TABLE `user`(id BIGINT PRIMARY KEY,avatar_file_id VARCHAR(32),is_deleted INT)");
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true); config.addMapper(PublicImageMapper.class);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config);
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(PublicImageMapper.class);
        minio = mock(MinioUtil.class);
        when(minio.getFile(eq("aiolife"), anyString())).thenAnswer(call -> new ByteArrayInputStream(image("png")));
        keys = mock(ApiKeyInterceptor.class); locks = mock(SecondaryLockInterceptor.class); activity = mock(UserLastActiveInterceptor.class);
        var registry = new TestRegistry();
        new SaTokenConfig(keys, locks, activity, mock(AccountStatusGuard.class)).addInterceptors(registry);
        var storage = new MinioConfig(); storage.setBucketName("aiolife");
        mvc = MockMvcBuilders.standaloneSetup(new PublicImageController(mapper, minio, storage))
                .addInterceptors(registry.interceptors()).build();
    }
    private static class TestRegistry extends InterceptorRegistry {
        HandlerInterceptor[] interceptors() { return getInterceptors().toArray(HandlerInterceptor[]::new); }
    }
    private String url(String id) { return "/public/images/" + id + ".png"; }
    private void template() {
        jdbc.update("INSERT INTO bank_card_cover_template VALUES(10,0,1)");
        jdbc.update("INSERT INTO file VALUES(?, 'system/bank-card-covers/test.png','image/png','bank_card_template_cover',10,42,0,0,NULL)", ID);
    }
    @Test void 已保存模板匿名读取缓存且不触发账号或二级锁() throws Exception {
        template();
        var response = mvc.perform(get(url(ID))).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(image("png")))
                .andExpect(header().string("Cache-Control", "public, max-age=86400, s-maxage=2592000"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn().getResponse();
        mvc.perform(get(url(ID)).header("If-None-Match", response.getHeader("ETag")))
                .andExpect(status().isNotModified()).andExpect(content().string(""));
        mvc.perform(head(url(ID))).andExpect(status().isOk()).andExpect(content().string(""));
        verifyNoInteractions(keys, locks, activity);
    }
    @Test void 停用保留底图但删除撤销源站访问() throws Exception {
        template(); jdbc.update("UPDATE bank_card_cover_template SET is_enabled=0");
        mvc.perform(get(url(ID))).andExpect(status().isOk());
        jdbc.update("UPDATE bank_card_cover_template SET is_deleted=1");
        mvc.perform(get(url(ID))).andExpect(status().isNotFound()).andExpect(header().string("Cache-Control", "no-store"));
    }
    @Test void 拒绝临时文件私人卡面其他公开文件和已删除文件() throws Exception {
        template();
        for (String type : new String[]{"bank_card_cover", "device", "b_video_cover"}) {
            jdbc.update("UPDATE file SET biz_type=?,is_public=1", type);
            assertNull(mapper.selectPublished(ID));
            mvc.perform(get(url(ID))).andExpect(status().isNotFound());
        }
        jdbc.update("UPDATE file SET biz_type='bank_card_template_cover',biz_id=NULL");
        mvc.perform(get(url(ID))).andExpect(status().isNotFound());
        jdbc.update("UPDATE file SET biz_id=10,is_deleted=1");
        mvc.perform(get(url(ID))).andExpect(status().isNotFound());
        verifyNoInteractions(minio);
    }
    @Test void 头像必须公开且当前绑定到所属用户() throws Exception {
        jdbc.update("INSERT INTO file VALUES(?,'42/avatar/test.png','image/png','avatar',NULL,42,0,1,NULL)", ID);
        mvc.perform(get(url(ID))).andExpect(status().isNotFound());
        jdbc.update("INSERT INTO `user` VALUES(43,?,0)", ID);
        mvc.perform(get(url(ID))).andExpect(status().isNotFound());
        jdbc.update("UPDATE `user` SET id=42");
        mvc.perform(get(url(ID))).andExpect(status().isOk());
        jdbc.update("UPDATE file SET is_public=0");
        mvc.perform(get(url(ID))).andExpect(status().isNotFound());
        jdbc.update("UPDATE file SET is_public=1");
        jdbc.update("UPDATE `user` SET avatar_file_id=?", NEXT);
        mvc.perform(get(url(ID))).andExpect(status().isNotFound());
    }
    @Test void 换图使用新文件ID旧图源站失效且新图可访问() throws Exception {
        template();
        jdbc.update("UPDATE file SET is_deleted=1");
        jdbc.update("INSERT INTO file VALUES(?,'system/bank-card-covers/new.png','image/png','bank_card_template_cover',10,42,0,0,NULL)", NEXT);
        mvc.perform(get(url(ID))).andExpect(status().isNotFound());
        mvc.perform(get(url(NEXT))).andExpect(status().isOk());
    }
    @Test void 伪装图片格式不匹配和存储异常均不返回可缓存成功响应() throws Exception {
        template();
        when(minio.getFile(anyString(), anyString())).thenReturn(new ByteArrayInputStream("<script>bad</script>".getBytes(StandardCharsets.UTF_8)));
        mvc.perform(get(url(ID))).andExpect(status().isNotFound()).andExpect(header().string("Cache-Control", "no-store"));
        when(minio.getFile(anyString(), anyString())).thenReturn(new ByteArrayInputStream(image("jpeg")));
        mvc.perform(get(url(ID))).andExpect(status().isNotFound());
        when(minio.getFile(anyString(), anyString())).thenThrow(new RuntimeException("missing"));
        mvc.perform(get(url(ID))).andExpect(status().isNotFound());
    }
}
