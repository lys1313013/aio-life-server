package top.aiolife.capture.service;

import static top.aiolife.capture.service.CaptureValues.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import top.aiolife.record.mapper.IDeviceMapper;
import top.aiolife.record.pojo.entity.DeviceEntity;
import top.aiolife.wardrobe.mapper.WardrobeItemMapper;
import top.aiolife.wardrobe.pojo.entity.WardrobeItemEntity;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 购物页面只是预填来源；确认后直接写入现有设备、衣物表，不保存订单或支出。 */
@Service
@RequiredArgsConstructor
public class PurchaseCaptureService {
    private final IDeviceMapper devices;
    private final WardrobeItemMapper wardrobe;
    private final CaptureSecurity access;
    private final CaptureRequests requests;
    private final ObjectMapper json;
    private static final Set<String> ORDER_STATES =
            Set.of(
                    "paid",
                    "completed",
                    "unpaid",
                    "refunded",
                    "partial_refund",
                    "presale",
                    "unknown");

    public JsonNode preview(long user, ObjectNode request) {
        ObjectNode draft = normalize(request, false);
        var out = json.createObjectNode();
        out.set("draft", draft);
        var warnings = json.createArrayNode();
        if (!draft.hasNonNull("paidAmount")) warnings.add("未识别实付金额，请核对并填写本次物品价格");
        if (!draft.hasNonNull("paidAt") && !draft.hasNonNull("orderedAt"))
            warnings.add("未识别购买日期，保存后将留空，可在设备或衣柜中补充");
        if (!Set.of("paid", "completed").contains(draft.path("orderStatus").asText()))
            warnings.add("页面支付状态不明确或涉及退款/预售，请人工确认是否创建物品及其购买价格");
        BigDecimal total = BigDecimal.ZERO;
        boolean complete = true;
        for (var line : draft.path("items")) {
            BigDecimal amount = money(line, "paidAmount", false);
            if (amount == null) complete = false;
            else total = total.add(amount);
        }
        BigDecimal paid = money(draft, "paidAmount", false);
        if (!complete || paid == null || total.compareTo(paid) != 0)
            warnings.add("请确认商品分摊金额，合计需等于页面实付（包含优惠、运费）");
        warnings.add("只创建设备或衣物，不保存采购订单、不记支出；再次打开同一页面不会永久查重");
        out.set("warnings", warnings);
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public JsonNode commit(long user, ObjectNode request) {
        fields(request, "idempotencyKey", "order", "amountConfirmed", "assets");
        ObjectNode order = normalize(object(request.path("order"), "order"), true);
        if (!request.path("amountConfirmed").isBoolean()
                || !request.path("amountConfirmed").booleanValue()) throw bad("请明确确认物品购买价格");
        JsonNode assetRequests = request.path("assets");
        if (!assetRequests.isArray() || assetRequests.isEmpty() || assetRequests.size() > 500)
            throw bad("至少选择一件设备或衣物，单次最多500件");
        for (JsonNode a : assetRequests) {
            String kind = choice(a, "kind", Set.of("device", "wardrobe"));
            access.menu(user, assetMenu(kind));
        }
        var attempt = requests.begin(user, "purchase-direct", request);
        JsonNode replay = requests.replay(attempt);
        if (replay != null) return replay;
        var result = json.createObjectNode();
        result.put("operation", "created");
        var created = result.putArray("assets");
        Set<String> units = new HashSet<>();
        for (JsonNode a : assetRequests) {
            fields(a, "sourceItemId", "unitIndex", "kind", "fields");
            String source = text(a, "sourceItemId", true, 128);
            int unit = integer(a, "unitIndex", 1, 100);
            if (!units.add(source + ":" + unit)) throw bad("同一商品件重复提交");
            JsonNode line = null;
            for (JsonNode candidate : order.path("items"))
                if (source.equals(candidate.path("sourceItemId").asText())) line = candidate;
            if (line == null || unit > line.path("quantity").intValue()) throw bad("商品件不存在或数量越界");
            String kind = choice(a, "kind", Set.of("device", "wardrobe"));
            ObjectNode f =
                    a.hasNonNull("fields")
                            ? object(a.get("fields"), "fields")
                            : json.createObjectNode();
            Long id = createAsset(user, kind, f, order, line, unit);
            created.addObject()
                    .put("sourceItemId", source)
                    .put("unitIndex", unit)
                    .put("kind", kind)
                    .put("id", id.toString());
        }
        return requests.finish(attempt, result);
    }

    private String assetMenu(String kind) {
        return "device".equals(kind) ? "/my-hub/device" : "/wardrobe";
    }

    private Long createAsset(
            long user, String kind, ObjectNode f, JsonNode order, JsonNode line, int unit) {
        fields(
                f,
                "name",
                "fileId",
                "type",
                "status",
                "categoryId",
                "color",
                "brand",
                "size",
                "memo",
                "remark");
        String name =
                f.hasNonNull("name") ? text(f, "name", true, 255) : text(line, "title", true, 255);
        String file = text(f, "fileId", false, 32);
        access.file(user, file, "device".equals(kind) ? "device" : "wardrobe_item");
        LocalDateTime date = date(order, "paidAt", false);
        if (date == null) date = date(order, "orderedAt", false);
        LocalDate purchaseDate = date == null ? null : date.toLocalDate();
        BigDecimal price =
                unitPrice(money(line, "paidAmount", true), line.path("quantity").intValue(), unit);
        if ("device".equals(kind)) {
            String deviceType = text(f, "type", false, 255);
            if (deviceType != null && !deviceType.isBlank())
                access.dictionary(user, "device_type", id(f, "type", true));
            if (f.hasNonNull("status")) choice(f, "status", Set.of("1", "2", "3", "4"));
            var d = new DeviceEntity();
            d.setUserId(user);
            d.setName(name);
            d.setSpec(text(line, "spec", false, 255));
            d.setType(text(f, "type", false, 255));
            d.setStatus(f.hasNonNull("status") ? text(f, "status", true, 255) : "1");
            d.setRemark(text(f, "remark", false, 255));
            d.setFileId(file);
            d.setPurchaseDate(purchaseDate);
            d.setPurchasePrice(price);
            d.setPurchasePlace(text(order, "shopName", false, 255));
            d.fillCreateCommonField(user);
            devices.insert(d);
            return d.getId();
        }
        if (name.length() > 100) throw bad("衣物名称最多100字，请编辑名称");
        Long category = id(f, "categoryId", false);
        access.category(user, category);
        var w = new WardrobeItemEntity();
        w.setUserId(user);
        w.setName(name);
        w.setCategoryId(category);
        w.setColor(text(f, "color", false, 50));
        w.setBrand(text(f, "brand", false, 100));
        w.setSize(text(f, "size", false, 20));
        w.setMemo(text(f, "memo", false, 500));
        w.setFileId(file);
        w.setPurchaseDate(purchaseDate);
        w.setPrice(price);
        w.fillCreateCommonField(user);
        wardrobe.insert(w);
        return w.getId();
    }

    public static BigDecimal unitPrice(BigDecimal line, int quantity, int unit) {
        long cents = line.movePointRight(2).longValueExact();
        return BigDecimal.valueOf(cents / quantity + (unit <= cents % quantity ? 1 : 0), 2);
    }

    public ObjectNode normalize(ObjectNode input, boolean strict) {
        fields(
                input,
                "platform",
                "orderNo",
                "sourceUrl",
                "shopName",
                "orderStatus",
                "paidAmount",
                "paidAt",
                "orderedAt",
                "freight",
                "discount",
                "items");
        String platform = choice(input, "platform", Set.of("jd", "taobao"));
        String no = text(input, "orderNo", false, 100);
        if (no != null && !no.isEmpty() && !no.matches("[0-9A-Za-z_-]+")) throw bad("订单号格式不正确");
        ObjectNode out = json.createObjectNode();
        out.put("platform", platform);
        if (no != null && !no.isEmpty()) out.put("orderNo", no);
        String source = text(input, "sourceUrl", true, 2048);
        try {
            URI uri = URI.create(source);
            String host = uri.getHost();
            boolean valid =
                    "https".equalsIgnoreCase(uri.getScheme())
                            && uri.getUserInfo() == null
                            && uri.getPort() == -1
                            && host != null
                            && ("jd".equals(platform)
                                    ? host.equals("jd.com") || host.endsWith(".jd.com")
                                    : host.equals("taobao.com")
                                            || host.endsWith(".taobao.com")
                                            || host.equals("tmall.com")
                                            || host.endsWith(".tmall.com"));
            if (!valid) throw bad("订单来源地址不属于所选平台");
            out.put(
                    "sourceUrl",
                    new URI(
                                    "https",
                                    null,
                                    host,
                                    -1,
                                    uri.getPath(),
                                    no == null || no.isEmpty()
                                            ? null
                                            : ("jd".equals(platform) ? "orderid=" : "biz_order_id=")
                                                    + no,
                                    null)
                            .toString());
        } catch (Exception e) {
            throw bad("订单来源地址无效");
        }
        String shop = text(input, "shopName", false, 255);
        if (shop != null) out.put("shopName", shop);
        out.put("orderStatus", choice(input, "orderStatus", ORDER_STATES));
        BigDecimal paid = money(input, "paidAmount", strict);
        if (paid != null) out.put("paidAmount", paid.toPlainString());
        for (String k : List.of("freight", "discount")) {
            BigDecimal v = money(input, k, false);
            if (v != null) out.put(k, v.toPlainString());
        }
        for (String k : List.of("paidAt", "orderedAt")) {
            LocalDateTime v = date(input, k, false);
            if (v != null) out.put(k, text(input, k, true, 19));
        }
        JsonNode lines = input.path("items");
        if (!lines.isArray() || lines.isEmpty() || lines.size() > 100) throw bad("订单须包含1至100条商品");
        var result = json.createArrayNode();
        Set<String> ids = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode line : lines) {
            object(line, "item");
            fields(
                    line,
                    "sourceItemId",
                    "title",
                    "sku",
                    "spec",
                    "quantity",
                    "paidAmount",
                    "imageUrl");
            var item = json.createObjectNode();
            String id = text(line, "sourceItemId", true, 128);
            if (!ids.add(id)) throw bad("本次草稿商品行标识重复");
            item.put("sourceItemId", id);
            item.put("title", text(line, "title", true, 255));
            item.put("quantity", integer(line, "quantity", 1, 100));
            for (String k : List.of("sku", "spec", "imageUrl")) {
                String v = text(line, k, false, "imageUrl".equals(k) ? 2048 : 255);
                if (v != null) item.put(k, v);
            }
            BigDecimal amount = money(line, "paidAmount", strict);
            if (amount != null) {
                sum = sum.add(amount);
                item.put("paidAmount", amount.toPlainString());
            }
            result.add(item);
        }
        if (strict && sum.compareTo(paid) != 0) throw bad("商品实付分摊合计必须等于订单实付");
        out.set("items", result);
        return out;
    }
}
