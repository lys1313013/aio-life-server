package top.aiolife.record.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.aiolife.record.mapper.UserBindMapper;
import top.aiolife.record.pojo.entity.UserBindEntity;
import top.aiolife.record.pojo.vo.WereadConnectionVO;
import top.aiolife.record.service.IWereadService;
import top.aiolife.record.weread.WereadClient;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 用户隔离的只读同步。不合并现有阅读记录，不写入微信读书。 */
@Service
@RequiredArgsConstructor
public class WereadServiceImpl implements IWereadService {
    private static final Set<String> MODES = Set.of("weekly", "monthly", "annually", "overall");
    private final UserBindMapper mapper;
    private final WereadClient client;

    private UserBindEntity find(long userId) {
        return mapper.selectOne(new LambdaQueryWrapper<UserBindEntity>()
                .eq(UserBindEntity::getUserId, userId)
                .eq(UserBindEntity::getPlatform, "weread"));
    }

    private UserBindEntity requireConnection(long userId) {
        var connection = find(userId);
        if (connection == null || !StringUtils.hasText(connection.getAccessToken())) {
            throw new IllegalStateException("请先在同步设置中连接微信读书");
        }
        return connection;
    }

    @Override
    public WereadConnectionVO connection() {
        var connection = find(StpUtil.getLoginIdAsLong());
        return view(connection);
    }

    private WereadConnectionVO view(UserBindEntity connection) {
        return new WereadConnectionVO(connection != null && StringUtils.hasText(connection.getAccessToken()),
                lastSyncTime(connection));
    }

    private JSONObject metadata(UserBindEntity connection) {
        if (connection == null || !StringUtils.hasText(connection.getMetaFields())) return new JSONObject();
        try {
            JSONObject result = JSON.parseObject(connection.getMetaFields());
            return result == null ? new JSONObject() : result;
        } catch (RuntimeException e) {
            throw new IllegalStateException("微信读书绑定配置格式无效，请重新绑定");
        }
    }

    private LocalDateTime lastSyncTime(UserBindEntity connection) {
        String value = metadata(connection).getString("lastSyncTime");
        if (!StringUtils.hasText(value)) return null;
        try { return LocalDateTime.parse(value.replace(' ', 'T')); }
        catch (java.time.format.DateTimeParseException e) { return null; }
    }

    private String credential(UserBindEntity connection) {
        return connection.getAccessToken();
    }

    @Override
    @Transactional
    public WereadConnectionVO connect(String apiKey) {
        long userId = StpUtil.getLoginIdAsLong();
        if (apiKey == null || !apiKey.matches("wrk-[A-Za-z0-9_-]{1,252}")) {
            throw new IllegalArgumentException("微信读书 Key 格式无效");
        }
        client.call(apiKey, "/shelf/sync", Map.of()); // 验证成功后才替换旧连接
        mapper.lockWereadUser(userId);
        var connection = find(userId);
        if (connection == null) {
            connection = new UserBindEntity();
            connection.fillCreateCommonField(userId);
            connection.setUserId(userId);
            connection.setPlatform("weread");
            JSONObject meta = new JSONObject();
            meta.put("connectionVersion", java.util.UUID.randomUUID().toString());
            connection.setMetaFields(meta.toJSONString());
            connection.setAccessToken(apiKey);
            mapper.insert(connection);
        } else {
            JSONObject meta = metadata(connection);
            meta.remove("lastSyncTime");
            meta.put("connectionVersion", java.util.UUID.randomUUID().toString());
            mapper.update(null, new LambdaUpdateWrapper<UserBindEntity>()
                    .eq(UserBindEntity::getUserId, userId)
                    .eq(UserBindEntity::getPlatform, "weread")
                    .set(UserBindEntity::getAccessToken, apiKey)
                    .set(UserBindEntity::getMetaFields, meta.toJSONString())
                    .set(UserBindEntity::getUpdateTime, LocalDateTime.now())
                    .set(UserBindEntity::getUpdateUser, userId));
            connection.setAccessToken(apiKey);
            connection.setMetaFields(meta.toJSONString());
        }
        return view(connection);
    }

    @Override
    @Transactional
    public void disconnect() {
        long userId = StpUtil.getLoginIdAsLong();
        mapper.lockWereadUser(userId);
        // 与账号绑定的解绑语义一致，并在逻辑删除前清除敏感凭证。
        mapper.update(null, new LambdaUpdateWrapper<UserBindEntity>()
                .eq(UserBindEntity::getUserId, userId)
                .eq(UserBindEntity::getPlatform, "weread")
                .set(UserBindEntity::getAccessToken, null)
                .setSql("meta_fields = JSON_REMOVE(COALESCE(meta_fields, JSON_OBJECT()), '$.lastSyncTime')")
                .set(UserBindEntity::getUpdateTime, LocalDateTime.now())
                .set(UserBindEntity::getUpdateUser, userId));
        mapper.delete(new LambdaQueryWrapper<UserBindEntity>()
                .eq(UserBindEntity::getUserId, userId)
                .eq(UserBindEntity::getPlatform, "weread"));
    }

    private void validateMode(String mode) {
        if (!MODES.contains(mode)) throw new IllegalArgumentException("不支持的统计周期");
    }

    private void validateBookId(String bookId) {
        if (bookId == null || !bookId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IllegalArgumentException("图书 ID 无效");
        }
    }

    @Override
    public JsonNode sync(String mode, long baseTime) {
        validateMode(mode);
        if (baseTime < 0 || baseTime > java.time.Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("统计日期不能晚于今天");
        }
        long userId = StpUtil.getLoginIdAsLong();
        var connection = requireConnection(userId);
        String key = credential(connection);
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.set("shelf", client.call(key, "/shelf/sync", Map.of()));
        result.set("notebooks", notebooks(key));
        result.set("stats", client.call(key, "/readdata/detail", Map.of("mode", mode, "baseTime", "overall".equals(mode) ? 0L : baseTime)));
        LocalDateTime now = LocalDateTime.now();
        int updated = mapper.update(null, new LambdaUpdateWrapper<UserBindEntity>()
                .eq(UserBindEntity::getId, connection.getId())
                .eq(UserBindEntity::getUserId, userId)
                .eq(UserBindEntity::getPlatform, "weread")
                .apply("JSON_UNQUOTE(JSON_EXTRACT(meta_fields, '$.connectionVersion')) = {0}",
                        metadata(connection).getString("connectionVersion"))
                .setSql("meta_fields = JSON_SET(COALESCE(meta_fields, JSON_OBJECT()), '$.lastSyncTime', {0})", now.toString())
                .set(UserBindEntity::getUpdateTime, now)
                .set(UserBindEntity::getUpdateUser, userId));
        if (updated == 0) throw new IllegalStateException("连接已变更，请重新同步");
        result.put("lastSyncTime", now.toString());
        return result;
    }

    @Override
    public JsonNode stats(String mode, long baseTime) {
        validateMode(mode);
        if (baseTime < 0 || baseTime > java.time.Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("统计日期不能晚于今天");
        }
        String key = credential(requireConnection(StpUtil.getLoginIdAsLong()));
        return client.call(key, "/readdata/detail", Map.of("mode", mode, "baseTime", "overall".equals(mode) ? 0L : baseTime));
    }

    private JsonNode notebooks(String key) {
        ArrayNode books = JsonNodeFactory.instance.arrayNode();
        Set<String> ids = new HashSet<>();
        Set<Long> cursors = new HashSet<>();
        Long cursor = null;
        for (int page = 0; page < 50; page++) {
            Map<String, Object> params = cursor == null ? Map.of("count", 100)
                    : Map.of("count", 100, "lastSort", cursor);
            JsonNode response = client.call(key, "/user/notebooks", params);
            JsonNode rows = response.path("books");
            for (JsonNode row : rows) if (ids.add(row.path("bookId").asText())) books.add(row);
            if (!hasMore(response)) {
                ObjectNode result = response.deepCopy();
                result.set("books", books);
                return result;
            }
            if (!rows.isArray() || rows.isEmpty() || !rows.get(rows.size() - 1).hasNonNull("sort")) {
                throw new IllegalStateException("笔记本分页异常，请重试");
            }
            cursor = rows.get(rows.size() - 1).path("sort").asLong();
            if (!cursors.add(cursor)) throw new IllegalStateException("笔记本分页未推进，请重试");
        }
        throw new IllegalStateException("笔记本数量超过单次读取上限，未完成同步");
    }

    private boolean hasMore(JsonNode response) {
        JsonNode more = response.path("hasMore");
        return more.isBoolean() ? more.asBoolean() : more.asInt() != 0;
    }

    @Override
    public JsonNode notes(String bookId) {
        validateBookId(bookId);
        String key = credential(requireConnection(StpUtil.getLoginIdAsLong()));
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.set("marks", client.call(key, "/book/bookmarklist", Map.of("bookId", bookId)));
        ArrayNode reviews = result.putArray("reviews");
        Set<String> ids = new HashSet<>();
        Set<Long> cursors = new HashSet<>();
        long cursor = 0;
        cursors.add(cursor);
        for (int page = 0; page < 50; page++) {
            JsonNode response = client.call(key, "/review/list/mine",
                    Map.of("bookid", bookId, "synckey", cursor, "count", 20));
            for (JsonNode review : response.path("reviews")) {
                String id = review.path("review").path("reviewId").asText(review.path("reviewId").asText());
                if (ids.add(id)) reviews.add(review);
            }
            if (!hasMore(response)) return result;
            if (!response.hasNonNull("synckey")) throw new IllegalStateException("点评分页异常，请重试");
            cursor = response.path("synckey").asLong();
            if (!cursors.add(cursor)) throw new IllegalStateException("点评分页未推进，请重试");
        }
        throw new IllegalStateException("点评数量超过单次读取上限，未完成同步");
    }

    @Override
    public JsonNode progress(String bookId) {
        validateBookId(bookId);
        String key = credential(requireConnection(StpUtil.getLoginIdAsLong()));
        return client.call(key, "/book/getprogress", Map.of("bookId", bookId));
    }
}
