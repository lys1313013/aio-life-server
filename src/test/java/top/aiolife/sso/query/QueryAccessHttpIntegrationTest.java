package top.aiolife.sso.query;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.AioLifeMain;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.mapper.UserSecondaryLockMenuMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.pojo.entity.UserSecondaryLockMenuEntity;
import top.aiolife.sso.service.IApiKeyService;
import top.aiolife.system.mapper.ISysMenuMapper;
import top.aiolife.system.pojo.entity.SysMenuEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 必须通过独立的测试 MySQL / Redis 环境运行，不能连接业务环境。 */
@ActiveProfiles("test")
@SpringBootTest(classes = AioLifeMain.class, properties = {
        "aio.life.neo4j.enabled=false",
        "aio.life.query-access.enabled=true",
        "aio.life.query-access.service-key=fixture-service-key-at-least-32-characters"})
@AutoConfigureMockMvc
@Transactional
class QueryAccessHttpIntegrationTest {
    private static final String KEY = "fixture-service-key-at-least-32-characters";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserMapper users;
    @Autowired QueryGrantStore store;
    @Autowired IApiKeyService apiKeys;
    @Autowired ISysMenuMapper menus;
    @Autowired UserSecondaryLockMenuMapper locks;
    private final List<String> tokens = new ArrayList<>();
    private final List<String> grants = new ArrayList<>();
    private long userId;
    private String session;

    @BeforeEach void setup() {
        UserEntity user = new UserEntity(); user.setUsername("query_" + UUID.randomUUID().toString().replace("-", ""));
        user.setNickname("查询测试账号"); user.setRole("user"); user.setIsDeleted(0); users.insert(user);
        userId = user.getId(); session = StpUtil.getStpLogic().createLoginSession(userId); tokens.add(session);
    }
    @AfterEach void cleanup() { grants.forEach(store::delete); tokens.forEach(StpUtil::logoutByTokenValue); }

    private JsonNode issue() throws Exception {
        String body = mvc.perform(post("/query/access-token").header("Authorization", "Bearer " + session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"appId\":\"aio-query\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rscode").value("0"))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsString();
        var data = json.readTree(body).path("data"); grants.add(data.path("grantId").asText()); return data;
    }
    private JsonNode evaluate(JsonNode grant, String key) throws Exception {
        String body = mvc.perform(post(QueryAccessController.INTERNAL_EVALUATE_PATH).header("X-AIO-Query-Service-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("token", grant.path("accessToken").asText(), "dataset", "time_record"))))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test void 显式授权返回字符串身份且密钥不能从公开凭据恢复() throws Exception {
        var grant = issue();
        assertEquals(300, grant.path("expiresIn").asInt());
        var result = evaluate(grant, KEY);
        assertEquals("0", result.path("rscode").asText());
        assertTrue(result.path("data").path("userId").isTextual());
        assertEquals(Long.toString(userId), result.path("data").path("userId").asText());
        assertEquals("ai_time_reader", result.path("data").path("role").asText());
        assertNotEquals(grant.path("accessToken").asText(), store.read(grant.path("grantId").asText()).tokenHash());
        assertNotEquals("0", evaluate(grant, "forged").path("rscode").asText());
    }

    @Test void 撤销及原会话失效立即拒绝() throws Exception {
        var revoked = issue();
        mvc.perform(delete("/query/grants/" + revoked.path("grantId").asText()).header("Authorization", "Bearer " + session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rscode").value("0"));
        assertNotEquals("0", evaluate(revoked, KEY).path("rscode").asText());
        var expiredSession = issue(); StpUtil.logoutByTokenValue(session);
        assertNotEquals("0", evaluate(expiredSession, KEY).path("rscode").asText());
    }

    @Test void 账号删除和过期授权不能访问() throws Exception {
        var expired = issue(); var old = store.read(expired.path("grantId").asText());
        store.delete(expired.path("grantId").asText());
        store.create(expired.path("grantId").asText(), new QueryAccessModels.Grant(old.tokenHash(), old.userId(), old.appId(), old.authorizerToken(), 1));
        assertNotEquals("0", evaluate(expired, KEY).path("rscode").asText());
        var deleted = issue(); users.deleteById(userId);
        assertNotEquals("0", evaluate(deleted, KEY).path("rscode").asText());
    }

    @Test void ApiKey不能签发授权且未知应用不能自动授权() throws Exception {
        var key = apiKeys.generateApiKey(userId, "query-fixture", 1);
        mvc.perform(post("/query/access-token").header("Authorization", "Bearer " + key.getApiKey())
                .contentType(MediaType.APPLICATION_JSON).content("{\"appId\":\"aio-query\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/query/access-token").header("Authorization", "Bearer " + session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"appId\":\"unknown\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(QueryAccessController.INTERNAL_EVALUATE_PATH)
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"invalid\",\"dataset\":\"time_record\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void 授权后重新锁定时迹仍然拒绝() throws Exception {
        var grant = issue();
        assertEquals("0", evaluate(grant, KEY).path("rscode").asText());
        // CI 已通过初始化脚本创建菜单；复用现有记录，空库才补充测试夹具。
        SysMenuEntity menu = menus.selectOne(new LambdaQueryWrapper<SysMenuEntity>()
                .eq(SysMenuEntity::getPath, "/time/time-tracker"));
        if (menu == null) {
            menu = new SysMenuEntity(); menu.setPath("/time/time-tracker"); menu.setName("查询锁测试");
            menu.setStatus(1); menu.setMobileStatus(1); menu.fillCreateCommonField(userId); menus.insert(menu);
        }
        UserSecondaryLockMenuEntity lock = new UserSecondaryLockMenuEntity();
        lock.setUserId(userId); lock.setMenuId(menu.getId()); lock.fillCreateCommonField(userId); locks.insert(lock);
        assertEquals("2001", evaluate(grant, KEY).path("rscode").asText());
    }
}
