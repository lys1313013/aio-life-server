package top.aiolife.database;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import top.aiolife.capture.service.CaptureRequests;
import top.aiolife.capture.service.CaptureSecurity;
import top.aiolife.capture.service.DoubanCaptureService;
import top.aiolife.capture.service.DoubanSourceGuard;
import top.aiolife.capture.service.DoubanSyncCoverService;
import top.aiolife.capture.service.PurchaseCaptureService;
import top.aiolife.capture.service.PurchaseSourceGuard;
import top.aiolife.config.JsonConfig;
import top.aiolife.core.exception.SecondaryLockRequiredException;
import top.aiolife.record.enums.FileBizType;
import top.aiolife.record.mapper.IDeviceMapper;
import top.aiolife.record.mapper.IExpenseMapper;
import top.aiolife.record.mapper.IFileMapper;
import top.aiolife.record.mapper.IMovieMapper;
import top.aiolife.record.mapper.ReadRecordMapper;
import top.aiolife.record.pojo.entity.DeviceEntity;
import top.aiolife.record.pojo.entity.ExpenseEntity;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.pojo.entity.MovieEntity;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;
import top.aiolife.record.pojo.req.MovieReq;
import top.aiolife.record.pojo.vo.FileVO;
import top.aiolife.record.service.IFileService;
import top.aiolife.record.service.IMovieService;
import top.aiolife.record.service.IReadRecordService;
import top.aiolife.record.service.UserDictDataService;
import top.aiolife.sso.service.SecondaryLockGuard;
import top.aiolife.wardrobe.mapper.WardrobeItemMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 隔离当前业务结构（无capture表）及Redis的真实事务测试。 */
@ActiveProfiles("test")
@SpringBootTest(
        classes = PhysicalTableEntitySelectIntegrationTest.DatabaseMapperTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({
    CaptureRequests.class,
    CaptureSecurity.class,
    DoubanCaptureService.class,
    DoubanSourceGuard.class,
    DoubanSyncCoverService.class,
    PurchaseCaptureService.class,
    PurchaseSourceGuard.class,
    JsonConfig.class
})
class CaptureIntegrationTest {
    @Autowired PurchaseCaptureService purchases;
    @Autowired DoubanCaptureService douban;
    @Autowired IDeviceMapper devices;
    @Autowired IExpenseMapper expenses;
    @Autowired ReadRecordMapper reads;
    @Autowired IMovieMapper movieMapper;
    @Autowired IFileMapper fileMapper;
    @Autowired WardrobeItemMapper wardrobe;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @MockitoBean SecondaryLockGuard locks;
    @MockitoBean UserDictDataService dictionaries;
    @MockitoBean IReadRecordService readService;
    @MockitoBean IMovieService movieService;
    @MockitoBean IFileService fileService;
    private static final long USER = 922001L;

    @BeforeEach
    void setup() {
        cleanup();
        jdbc.update(
                "INSERT INTO `user` (id,username,password,nickname,is_deleted) VALUES"
                    + " (?,?,?,'采集测试用户',0)",
                USER,
                "capture-fixture",
                "synthetic-test-only");
        var d = new UserDictDataEntity();
        d.setId(987L);
        d.setDictLabel("测试设备");
        when(dictionaries.listUserVisibleDictData(anyLong(), eq("device_type")))
                .thenReturn(List.of(d));
    }

    @AfterEach
    void cleanup() {
        for (String table : List.of("device", "wardrobe_item", "read_record", "movie", "expense"))
            jdbc.update("DELETE FROM " + table + " WHERE user_id=?", USER);
        jdbc.update("DELETE FROM `file` WHERE create_user=?", USER);
        jdbc.update("DELETE FROM `user` WHERE id=?", USER);
        var keys = redis.keys("aio:capture:request:v2:" + USER + ":*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }

    private ObjectNode order() {
        var n = json.createObjectNode();
        n.put("platform", "jd");
        n.put("orderNo", "9007199254740993555");
        n.put("sourceUrl", "https://details.jd.com/normal/item.action?PassKey=private");
        n.put("shopName", "模拟测试商家");
        n.put("orderStatus", "paid");
        n.put("paidAmount", "10.01");
        n.put("paidAt", "2026-10-22 12:30:00");
        var line = n.putArray("items").addObject();
        line.put("sourceItemId", "line:0");
        line.put("title", "模拟商品");
        line.put("sku", "9007199254740993666");
        line.put("quantity", 2);
        line.put("paidAmount", "10.01");
        return n;
    }

    private ObjectNode request() {
        var n = json.createObjectNode();
        n.put("idempotencyKey", UUID.randomUUID().toString());
        n.set("order", order());
        n.put("amountConfirmed", true);
        n.putArray("assets");
        return n;
    }

    private void asset(ObjectNode req, String kind, int unit) {
        var a = ((com.fasterxml.jackson.databind.node.ArrayNode) req.get("assets")).addObject();
        a.put("sourceItemId", "line:0");
        a.put("unitIndex", unit);
        a.put("kind", kind);
        a.putObject("fields").put("name", "模拟" + kind);
    }

    private long countDevices() {
        return devices.selectCount(
                new LambdaQueryWrapper<DeviceEntity>().eq(DeviceEntity::getUserId, USER));
    }

    @Test
    void 无采集表直接保存两类物品且不新增支出并短期重放() {
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE"
                                + " table_schema=DATABASE() AND table_name LIKE 'capture_%'",
                        Integer.class));
        var req = request();
        asset(req, "device", 1);
        asset(req, "wardrobe", 2);
        var result = purchases.commit(USER, req);
        var replay = purchases.commit(USER, req.deepCopy());
        assertEquals(result, replay);
        assertEquals(2, result.path("assets").size());
        assertFalse(result.has("id"));
        assertTrue(result.hasNonNull("replayExpiresAt"));
        long deviceId = Long.parseLong(result.path("assets").get(0).path("id").asText());
        long wardrobeId = Long.parseLong(result.path("assets").get(1).path("id").asText());
        assertEquals(new BigDecimal("5.01"), devices.selectById(deviceId).getPurchasePrice());
        assertEquals(new BigDecimal("5.00"), wardrobe.selectById(wardrobeId).getPrice());
        assertEquals("line:0", devices.selectById(deviceId).getSourceItemId());
        assertEquals(1, devices.selectById(deviceId).getSourceUnitIndex());
        assertEquals("line:0", wardrobe.selectById(wardrobeId).getSourceItemId());
        assertEquals(2, wardrobe.selectById(wardrobeId).getSourceUnitIndex());
        assertEquals(
                0,
                expenses.selectCount(
                        new LambdaQueryWrapper<ExpenseEntity>()
                                .eq(ExpenseEntity::getUserId, USER)));
        assertEquals(1, countDevices());
    }

    @Test
    void 采购历史费用和已有资产关联字段明确拒绝() {
        for (String field : List.of("expense", "version")) {
            var req = request();
            asset(req, "device", 1);
            req.put(field, "unsupported");
            assertThrows(RuntimeException.class, () -> purchases.commit(USER, req));
        }
        var req = request();
        asset(req, "device", 1);
        ((ObjectNode) req.path("assets").get(0)).put("mode", "link").put("id", "123");
        assertThrows(RuntimeException.class, () -> purchases.commit(USER, req));
    }

    @Test
    void 无订单号可预览但不能保存无来源物品() {
        var input = order(); input.remove("orderNo");
        var result = purchases.preview(USER, input);
        assertTrue(result.path("existingAssets").isEmpty());
        var req = request(); req.set("order", input); asset(req, "device", 1);
        assertThrows(RuntimeException.class, () -> purchases.commit(USER, req));
        assertFalse(result.has("expenseCandidates"));
        assertFalse(result.path("draft").path("sourceUrl").asText().contains("PassKey"));
    }

    @Test
    void 不选择物品不允许空提交() {
        assertThrows(RuntimeException.class, () -> purchases.commit(USER, request()));
    }

    @Test
    void 幂等键换请求内容拒绝() {
        var req = request();
        asset(req, "device", 1);
        purchases.commit(USER, req);
        var changed = req.deepCopy();
        ((ObjectNode) changed.path("assets").get(0).path("fields")).put("remark", "different");
        assertThrows(RuntimeException.class, () -> purchases.commit(USER, changed));
        assertEquals(1, countDevices());
    }

    @Test
    void 设备菜单锁定后重放仍被拦截() {
        var req = request();
        asset(req, "device", 1);
        purchases.commit(USER, req);
        doThrow(new SecondaryLockRequiredException("/my-hub/device"))
                .when(locks)
                .checkMenus(USER, "/my-hub/device");
        assertThrows(SecondaryLockRequiredException.class, () -> purchases.commit(USER, req));
    }

    @Test
    void 后一件无权图片失败回滚前一件且明确回滚后原键可重试() {
        var req = request();
        asset(req, "device", 1);
        asset(req, "wardrobe", 2);
        ((ObjectNode) req.path("assets").get(1).path("fields")).put("fileId", "foreign-image");
        assertThrows(RuntimeException.class, () -> purchases.commit(USER, req));
        assertEquals(0, countDevices());
        assertTrue(redis.keys("aio:capture:request:v2:" + USER + ":*").isEmpty());
        ((ObjectNode) req.path("assets").get(1).path("fields")).remove("fileId");
        assertEquals(2, purchases.commit(USER, req).path("assets").size());
    }

    @Test
    void 新请求键重新采集同订单商品返回原物品并保留编辑() {
        var req = request(); asset(req, "device", 1); asset(req, "wardrobe", 2);
        var saved = purchases.commit(USER, req);
        long deviceId = Long.parseLong(saved.path("assets").get(0).path("id").asText());
        jdbc.update("UPDATE device SET name='服务器新名称' WHERE id=?", deviceId);
        var second = req.deepCopy(); second.put("idempotencyKey", UUID.randomUUID().toString());
        ((ObjectNode) second.path("assets").get(0)).put("kind", "wardrobe");
        var replay = purchases.commit(USER, second);
        assertEquals("existing", replay.path("operation").asText());
        assertEquals(saved.path("assets"), replay.path("assets"));
        assertEquals("服务器新名称", devices.selectById(deviceId).getName());
        var row = devices.selectById(deviceId);
        assertEquals("9007199254740993555", row.getSourceOrderId());
        assertEquals("9007199254740993666", row.getSourceProductId());
        assertEquals("jd", row.getSourcePlatform());
        var preview = purchases.preview(USER, order());
        assertEquals(saved.path("assets"), preview.path("existingAssets"));
        assertEquals(1, countDevices());
    }

    @Test
    void 跨归属查重重放仍检查实际物品菜单锁() {
        var first = request(); asset(first, "device", 1); purchases.commit(USER, first);
        var second = request(); asset(second, "wardrobe", 1);
        assertEquals("device", purchases.commit(USER, second).path("assets").get(0).path("kind").asText());
        doThrow(new SecondaryLockRequiredException("/my-hub/device")).when(locks).checkMenus(USER, "/my-hub/device");
        assertThrows(SecondaryLockRequiredException.class, () -> purchases.commit(USER, second));
    }

    @Test
    void 部分已保存只创建剩余件且删除后可重新录入() {
        var first = request(); asset(first, "device", 1); purchases.commit(USER, first);
        var next = request(); asset(next, "device", 1); asset(next, "wardrobe", 2);
        var result = purchases.commit(USER, next);
        assertEquals("mixed", result.path("operation").asText());
        assertEquals(1, result.path("createdCount").asInt());
        long id = Long.parseLong(result.path("assets").get(0).path("id").asText());
        devices.deleteById(id);
        var recreated = request(); asset(recreated, "device", 1);
        assertNotEquals(Long.toString(id), purchases.commit(USER, recreated).path("assets").get(0).path("id").asText());
    }

    @Test
    void 不同平台订单或明细独立保存且同明细商品变更拒绝() {
        var first = request(); asset(first, "device", 1); purchases.commit(USER, first);
        for (String field : List.of("orderNo", "sourceItemId", "platform")) {
            var req = request(); asset(req, "device", 1);
            var order = (ObjectNode) req.path("order");
            if (field.equals("orderNo")) order.put(field, "other-order");
            else if (field.equals("platform")) { order.put(field, "taobao"); order.put("sourceUrl", "https://trade.taobao.com/trade/detail/trade_item_detail.htm"); }
            else { ((ObjectNode) order.path("items").get(0)).put(field, "other-product");
                if (field.equals("sourceItemId")) ((ObjectNode) req.path("assets").get(0)).put(field, "other-product"); }
            assertEquals("created", purchases.commit(USER, req).path("operation").asText());
        }
        var changed = request(); asset(changed, "device", 1);
        ((ObjectNode) changed.path("order").path("items").get(0)).put("sku", "other-product");
        assertThrows(RuntimeException.class, () -> purchases.commit(USER, changed));
        assertThrows(RuntimeException.class,
                () -> purchases.preview(USER, (ObjectNode) changed.path("order")));
        assertEquals(4, countDevices());
    }

    @Test
    void 查重不泄露锁定模块和其他用户记录() {
        var req = request(); asset(req, "device", 1); purchases.commit(USER, req);
        assertTrue(purchases.preview(USER + 1, order()).path("existingAssets").isEmpty());
        doThrow(new SecondaryLockRequiredException("/my-hub/device")).when(locks).checkMenus(USER, "/my-hub/device");
        assertThrows(SecondaryLockRequiredException.class, () -> purchases.preview(USER, order()));
    }

    @Test
    void 缺商品ID拒绝保存且不会伪造来源() {
        var req = request(); asset(req, "device", 1);
        ((ObjectNode) req.path("order").path("items").get(0)).remove("sku");
        assertThrows(RuntimeException.class, () -> purchases.commit(USER, req));
        assertEquals(0, countDevices());
    }

    @Test
    void 不同请求键跨设备衣柜并发采集只创建一件() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var a = executor.submit(() -> { start.await(); var req = request(); asset(req, "device", 1); return purchases.commit(USER, req); });
            var b = executor.submit(() -> { start.await(); var req = request(); asset(req, "wardrobe", 1); return purchases.commit(USER, req); });
            start.countDown();
            var one = a.get(15, TimeUnit.SECONDS); var two = b.get(15, TimeUnit.SECONDS);
            assertEquals(one.path("assets"), two.path("assets"));
            assertEquals(1, countDevices() + jdbc.queryForObject("SELECT COUNT(*) FROM wardrobe_item WHERE user_id=? AND is_deleted=0", Long.class, USER));
        } finally { executor.shutdownNow(); }
    }

    @Test
    void 只选第二件重新采集保留件序号且第一件独立新增() {
        var first = request(); asset(first, "device", 2);
        String id = purchases.commit(USER, first).path("assets").get(0).path("id").asText();
        var next = request(); asset(next, "device", 2);
        assertEquals(id, purchases.commit(USER, next).path("assets").get(0).path("id").asText());
        assertEquals(1, countDevices());
        var preview = purchases.preview(USER, order());
        assertEquals(1, preview.path("existingAssets").size());
        assertEquals(2, preview.path("existingAssets").get(0).path("unitIndex").asInt());
        assertEquals(id, preview.path("existingAssets").get(0).path("id").asText());
        var add = request(); asset(add, "device", 1); asset(add, "wardrobe", 2);
        ((ObjectNode) add.path("assets").get(1)).put("existingId", id);
        var result = purchases.commit(USER, add);
        assertEquals(1, result.path("createdCount").asInt());
        assertNotEquals(id, result.path("assets").get(0).path("id").asText());
        assertEquals(id, result.path("assets").get(1).path("id").asText());
        assertEquals(2, countDevices());
    }

    @Test
    void 同商品不同明细按来源分别保存且页面重排不串件() {
        var req = request();
        var order = (ObjectNode) req.path("order");
        order.put("paidAmount", "20.02");
        var firstLine = (ObjectNode) order.path("items").get(0);
        firstLine.put("spec", "红色");
        var secondLine = firstLine.deepCopy().put("sourceItemId", "line:1").put("spec", "蓝色");
        ((com.fasterxml.jackson.databind.node.ArrayNode) order.path("items")).add(secondLine);
        asset(req, "device", 1);
        asset(req, "wardrobe", 1);
        ((ObjectNode) req.path("assets").get(1)).put("sourceItemId", "line:1");
        var saved = purchases.commit(USER, req);
        assertNotEquals(saved.path("assets").get(0).path("id"), saved.path("assets").get(1).path("id"));
        assertEquals(2, saved.path("createdCount").asInt());
        var reordered = order.deepCopy();
        reordered.putArray("items").add(secondLine).add(firstLine);
        var preview = purchases.preview(USER, reordered).path("existingAssets");
        assertEquals(saved.path("assets").get(1), preview.get(0));
        assertEquals(saved.path("assets").get(0), preview.get(1));
        var next = req.deepCopy();
        next.put("idempotencyKey", UUID.randomUUID().toString());
        next.set("order", reordered);
        assertEquals(saved.path("assets"), purchases.commit(USER, next).path("assets"));
        assertEquals(1, countDevices());
    }

    @Test
    void 已有物品不能借用到同商品的其他件() {
        var first = request(); asset(first, "device", 2);
        String id = purchases.commit(USER, first).path("assets").get(0).path("id").asText();
        var next = request(); asset(next, "wardrobe", 1);
        ((ObjectNode) next.path("assets").get(0)).put("existingId", id);
        assertThrows(RuntimeException.class, () -> purchases.commit(USER, next));
        assertEquals(1, countDevices());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wardrobe_item WHERE user_id=?", Long.class, USER));
    }

    @Test
    void 来源冲突先检查实际物品菜单权限() {
        var first = request(); asset(first, "device", 1); purchases.commit(USER, first);
        var changed = request(); asset(changed, "wardrobe", 1);
        ((ObjectNode) changed.path("order").path("items").get(0)).put("sku", "other-product");
        doThrow(new SecondaryLockRequiredException("/my-hub/device"))
                .when(locks).checkMenus(USER, "/my-hub/device");
        assertThrows(SecondaryLockRequiredException.class, () -> purchases.commit(USER, changed));
        assertThrows(SecondaryLockRequiredException.class,
                () -> purchases.preview(USER, (ObjectNode) changed.path("order")));
    }

    @Test
    void 跨设备衣柜存在相同来源件时拒绝任选一条() {
        var req = request(); asset(req, "device", 1); asset(req, "wardrobe", 2);
        purchases.commit(USER, req);
        jdbc.update("UPDATE wardrobe_item SET source_unit_index=1 WHERE user_id=?", USER);
        assertThrows(RuntimeException.class, () -> purchases.preview(USER, order()));
        var next = request(); asset(next, "device", 1);
        assertThrows(RuntimeException.class, () -> purchases.commit(USER, next));
        assertEquals(1, countDevices());
    }

    @Test
    void 保留物品已删除时拒绝自动补建() {
        var req = request(); asset(req, "device", 1);
        String id = purchases.commit(USER, req).path("assets").get(0).path("id").asText();
        devices.deleteById(Long.parseLong(id));
        var next = request(); asset(next, "device", 1); asset(next, "wardrobe", 2);
        ((ObjectNode) next.path("assets").get(0)).put("existingId", id);
        assertThrows(RuntimeException.class, () -> purchases.commit(USER, next));
        assertEquals(0, countDevices());
    }

    private ObjectNode book() {
        var n =
                json.createObjectNode()
                        .put("url", "https://book.douban.com/subject/999991/")
                        .put("idempotencyKey", UUID.randomUUID().toString())
                        .put("action", "create");
        n.putObject("fields")
                .put("title", "模拟书籍")
                .put("type", 1)
                .put("status", "in_progress")
                .put("currentProgress", 15)
                .put("totalProgress", 100)
                .put("remark", "保留笔记");
        return n;
    }

    @Test
    void 豆瓣原表查重更新保留笔记且不依赖新表() {
        var req = book();
        var first = douban.commit(USER, req);
        var duplicate = douban.commit(USER, book());
        assertEquals(first.path("id"), duplicate.path("id"));
        assertEquals("existing", duplicate.path("operation").asText());
        var previewReq = json.createObjectNode().put("url", req.path("url").asText());
        previewReq.putObject("metadata").put("title", "DOM书名");
        var preview = douban.preview(USER, previewReq);
        verifyNoInteractions(readService);
        var update =
                json.createObjectNode()
                        .put("url", req.path("url").asText())
                        .put("action", "update")
                        .put("idempotencyKey", UUID.randomUUID().toString())
                        .put("existingId", first.path("id").asText())
                        .put("version", preview.path("existing").path("version").asText());
        update.putObject("fields").put("status", "on_hold");
        douban.commit(USER, update);
        var row = reads.selectById(Long.parseLong(first.path("id").asText()));
        assertEquals(15, row.getCurrentProgress());
        assertEquals("保留笔记", row.getRemark());
    }

    @Test
    void 新增未给总进度返回版本可直接更新() {
        var req = book();
        ((ObjectNode) req.get("fields")).remove("totalProgress");
        var created = douban.commit(USER, req);
        var update =
                json.createObjectNode()
                        .put("url", req.path("url").asText())
                        .put("action", "update")
                        .put("idempotencyKey", UUID.randomUUID().toString())
                        .put("existingId", created.path("id").asText())
                        .put("version", created.path("version").asText());
        update.putObject("fields").put("status", "on_hold");
        assertEquals("updated", douban.commit(USER, update).path("operation").asText());
    }

    @Test
    void 并发同键最多写入一次后续重试返回原结果() throws Exception {
        var req = request();
        asset(req, "device", 1);
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var a =
                    executor.submit(
                            () -> {
                                start.await();
                                try {
                                    return purchases.commit(USER, req.deepCopy());
                                } catch (RuntimeException e) {
                                    return null;
                                }
                            });
            var b =
                    executor.submit(
                            () -> {
                                start.await();
                                try {
                                    return purchases.commit(USER, req.deepCopy());
                                } catch (RuntimeException e) {
                                    return null;
                                }
                            });
            start.countDown();
            JsonNode first = a.get(15, TimeUnit.SECONDS), second = b.get(15, TimeUnit.SECONDS);
            assertTrue(first != null || second != null);
            assertEquals(1, countDevices());
            JsonNode success = first != null ? first : second;
            assertEquals(success, purchases.commit(USER, req));
        } finally {
            executor.shutdownNow();
        }
    }

    private ObjectNode nativeSync(String status) {
        var request =
                json.createObjectNode()
                        .put("url", "https://movie.douban.com/subject/999992/")
                        .put("idempotencyKey", UUID.randomUUID().toString())
                        .put("status", status);
        request.putObject("metadata")
                .put("title", "原生标记模拟影视")
                .put("director", "页面导演")
                .put("type", 2)
                .put("totalProgress", 12);
        return request;
    }

    @Test
    void 原生看过直接创建电影并填满进度重复同状态不修改() {
        var request = nativeSync("completed");
        var first = douban.sync(USER, request);
        long id = Long.parseLong(first.path("id").asText());
        var row = movieMapper.selectById(id);
        assertEquals("created", first.path("operation").asText());
        assertEquals(12, row.getCurrentProgress());
        assertNotNull(row.getFinishTime());
        assertNull(row.getRating());
        assertEquals(first, douban.sync(USER, request));
        // 用明显旧的审计时间证明同状态无SQL更新，而非依赖秒级时间碰巧相同。
        jdbc.update("UPDATE movie SET update_time='2020-01-01 00:00:00' WHERE id=?", id);
        var unchanged = douban.sync(USER, nativeSync("completed"));
        assertEquals("unchanged", unchanged.path("operation").asText());
        assertEquals(first.path("id"), unchanged.path("id"));
        assertEquals(
                LocalDateTime.of(2020, 1, 1, 0, 0), movieMapper.selectById(id).getUpdateTime());
        verifyNoInteractions(fileService, movieService);
    }

    @Test
    void 原生状态来回切换只修改状态进度完成时间保留全部个人字段() {
        var movie = new MovieEntity();
        movie.setUserId(USER);
        movie.setUrl("https://movie.douban.com/subject/999992/");
        movie.setTitle("我的影片名称");
        movie.setDirector("原导演");
        movie.setType(5);
        movie.setTotalProgress(14);
        movie.setCurrentProgress(3);
        movie.setStatus(ProgressStatusEnum.IN_PROGRESS);
        movie.setRating(4);
        movie.setRemark("私人观影笔记");
        movie.setFileId("personal-cover");
        movie.setStartTime(LocalDateTime.of(2019, 2, 3, 4, 5));
        movie.fillCreateCommonField(USER);
        movieMapper.insert(movie);
        var completed = douban.sync(USER, nativeSync("completed"));
        assertEquals("updated", completed.path("operation").asText());
        var row = movieMapper.selectById(movie.getId());
        assertEquals(14, row.getCurrentProgress());
        assertNotNull(row.getFinishTime());
        var waiting = douban.sync(USER, nativeSync("not_started"));
        assertEquals("updated", waiting.path("operation").asText());
        row = movieMapper.selectById(movie.getId());
        assertEquals(0, row.getCurrentProgress());
        assertNull(row.getFinishTime());
        assertEquals(movie.getStartTime(), row.getStartTime());
        assertEquals("我的影片名称", row.getTitle());
        assertEquals("原导演", row.getDirector());
        assertEquals(5, row.getType());
        assertEquals(14, row.getTotalProgress());
        assertEquals(4, row.getRating());
        assertEquals("私人观影笔记", row.getRemark());
        assertEquals("personal-cover", row.getFileId());
        verifyNoInteractions(fileService, movieService);
    }

    @Test
    void 原生同步拒绝书籍其他状态和个人字段注入() {
        var book = nativeSync("completed");
        book.put("url", "https://book.douban.com/subject/999992/");
        assertThrows(RuntimeException.class, () -> douban.sync(USER, book));
        assertThrows(RuntimeException.class, () -> douban.sync(USER, nativeSync("in_progress")));
        var injected = nativeSync("completed");
        ((ObjectNode) injected.get("metadata")).put("rating", 5);
        assertThrows(RuntimeException.class, () -> douban.sync(USER, injected));
        assertEquals(
                0,
                movieMapper.selectCount(
                        new LambdaQueryWrapper<MovieEntity>().eq(MovieEntity::getUserId, USER)));
    }

    @Test
    void 原生同步菜单锁和无效用户拒绝且不会跨用户修改() {
        doThrow(new SecondaryLockRequiredException("/record/movie"))
                .when(locks)
                .checkMenus(USER, "/record/movie");
        assertThrows(
                SecondaryLockRequiredException.class,
                () -> douban.sync(USER, nativeSync("completed")));
        reset(locks);
        assertThrows(RuntimeException.class, () -> douban.sync(USER + 2, nativeSync("completed")));
        var foreign = new MovieEntity();
        foreign.setUserId(USER + 1);
        foreign.setUrl("https://movie.douban.com/subject/999992/");
        foreign.setTitle("其他用户记录");
        foreign.setType(1);
        foreign.setStatus(ProgressStatusEnum.NOT_STARTED);
        foreign.fillCreateCommonField(USER + 1);
        movieMapper.insert(foreign);
        try {
            var own = douban.sync(USER, nativeSync("completed"));
            assertNotEquals(foreign.getId().toString(), own.path("id").asText());
            assertEquals(
                    ProgressStatusEnum.NOT_STARTED,
                    movieMapper.selectById(foreign.getId()).getStatus());
        } finally {
            jdbc.update("DELETE FROM movie WHERE id=?", foreign.getId());
        }
    }

    @Test
    void 封面独立事务失败不回滚电影状态且返回警告() {
        var request = nativeSync("completed");
        ((ObjectNode) request.get("metadata"))
                .put("coverImgUrl", "https://img.doubanio.com/view/photo/example.jpg");
        when(fileService.uploadFromUrl(anyString(), eq(FileBizType.MOVIE)))
                .thenThrow(new IllegalStateException("模拟封面不可用"));
        var result = douban.sync(USER, request);
        assertEquals("created", result.path("operation").asText());
        assertFalse(result.path("warnings").isEmpty());
        var row = movieMapper.selectById(Long.parseLong(result.path("id").asText()));
        assertEquals(ProgressStatusEnum.COMPLETED, row.getStatus());
        assertNull(row.getFileId());
        assertEquals(result, douban.sync(USER, request));
    }

    @Test
    void 新影片缺标题时复用元数据解析且安全封面可关联() {
        var parsed = new MovieReq();
        parsed.setTitle("解析取得的名称");
        parsed.setType(2);
        parsed.setTotalProgress(8);
        parsed.setCoverImgUrl("https://img.doubanio.com/view/photo/example.jpg");
        when(movieService.parseDoubanMetadata(anyString())).thenReturn(parsed);
        when(fileService.uploadFromUrl(anyString(), eq(FileBizType.MOVIE)))
                .thenAnswer(
                        inv -> {
                            var file = new FileEntity();
                            file.setId("0123456789abcdef0123456789abcdef");
                            file.setFileName("capture-sync.png");
                            file.setFileType("image/png");
                            file.setBizType("movie");
                            file.fillCreateCommonField(USER);
                            fileMapper.insert(file);
                            var uploaded = new FileVO();
                            uploaded.setId(file.getId());
                            return uploaded;
                        });
        var request = nativeSync("not_started");
        request.remove("metadata");
        var result = douban.sync(USER, request);
        var row = movieMapper.selectById(Long.parseLong(result.path("id").asText()));
        assertEquals("解析取得的名称", row.getTitle());
        assertEquals(8, row.getTotalProgress());
        assertEquals(0, row.getCurrentProgress());
        assertNotNull(row.getFileId());
        assertNull(row.getFinishTime());
        verify(movieService).parseDoubanMetadata("https://movie.douban.com/subject/999992/");
    }

    @Test
    void 不同请求键并发同步同一豆瓣条目不会重复创建() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var a =
                    executor.submit(
                            () -> {
                                start.await();
                                return douban.sync(USER, nativeSync("completed"));
                            });
            var b =
                    executor.submit(
                            () -> {
                                start.await();
                                return douban.sync(USER, nativeSync("completed"));
                            });
            start.countDown();
            var one = a.get(15, TimeUnit.SECONDS);
            var two = b.get(15, TimeUnit.SECONDS);
            assertEquals(one.path("id"), two.path("id"));
            assertEquals(
                    1,
                    movieMapper.selectCount(
                            new LambdaQueryWrapper<MovieEntity>()
                                    .eq(MovieEntity::getUserId, USER)));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 取消标记逻辑删除保留个人数据并可重放和重新标记() {
        var created = douban.sync(USER, nativeSync("completed"));
        long id = Long.parseLong(created.path("id").asText());
        jdbc.update("UPDATE movie SET remark='保留的个人笔记',rating=4 WHERE id=?", id);
        var remove = nativeSync("removed");
        remove.remove("metadata");
        var result = douban.sync(USER, remove);
        assertEquals("deleted", result.path("operation").asText());
        assertEquals(created.path("id"), result.path("id"));
        assertNull(movieMapper.selectById(id));
        assertEquals(
                1,
                jdbc.queryForObject("SELECT is_deleted FROM movie WHERE id=?", Integer.class, id));
        assertEquals(
                "保留的个人笔记",
                jdbc.queryForObject("SELECT remark FROM movie WHERE id=?", String.class, id));
        assertEquals(
                4, jdbc.queryForObject("SELECT rating FROM movie WHERE id=?", Integer.class, id));
        assertEquals(result, douban.sync(USER, remove));
        var absent = douban.sync(USER, nativeSync("removed"));
        assertEquals("absent", absent.path("operation").asText());
        assertTrue(absent.path("id").isNull());
        var marked = douban.sync(USER, nativeSync("not_started"));
        assertEquals("created", marked.path("operation").asText());
        assertNotEquals(created.path("id"), marked.path("id"));
        // 延迟重放旧删除请求只返回旧结果，绝不能删除随后重新标记的新记录。
        assertEquals(result, douban.sync(USER, remove));
        assertNotNull(movieMapper.selectById(Long.parseLong(marked.path("id").asText())));
        verifyNoInteractions(fileService, movieService);
    }

    @Test
    void 取消不删除其他用户同条目且不解析或上传() {
        var foreign = new MovieEntity();
        foreign.setUserId(USER + 1);
        foreign.setUrl("https://movie.douban.com/subject/999992/");
        foreign.setTitle("其他用户记录");
        foreign.setType(1);
        foreign.setStatus(ProgressStatusEnum.COMPLETED);
        foreign.fillCreateCommonField(USER + 1);
        movieMapper.insert(foreign);
        try {
            var request = nativeSync("removed");
            request.remove("metadata");
            var result = douban.sync(USER, request);
            assertEquals("absent", result.path("operation").asText());
            assertTrue(result.path("id").isNull());
            assertNotNull(movieMapper.selectById(foreign.getId()));
            verifyNoInteractions(fileService, movieService);
        } finally {
            jdbc.update("DELETE FROM movie WHERE id=?", foreign.getId());
        }
    }
}
