package top.aiolife.capture;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import top.aiolife.capture.service.CaptureRequests;
import top.aiolife.capture.service.CaptureValues;
import top.aiolife.capture.service.PurchaseCaptureService;

import java.math.BigDecimal;

class CaptureValidationTest {
    private final ObjectMapper json = new ObjectMapper();
    private final PurchaseCaptureService service =
            new PurchaseCaptureService(null, null, null, null, json, null);

    private ObjectNode order() throws Exception {
        return (ObjectNode)
                json.readTree(
                        """
                        {"platform":"jd","orderNo":"1234","sourceUrl":"https://details.jd.com/normal/item.action?orderid=1234&PassKey=PRIVATE#private","orderStatus":"paid","paidAmount":"10.01","paidAt":"2026-10-22 12:30:00","items":[{"sourceItemId":"line1","title":"模拟商品","quantity":3,"paidAmount":"10.01"}]}
                        """);
    }

    @Test
    void 私密参数被移除且保留来源身份() throws Exception {
        var out = service.normalize(order(), true);
        assertEquals(
                "https://details.jd.com/normal/item.action?orderid=1234",
                out.path("sourceUrl").asText());
        assertFalse(out.toString().contains("PRIVATE"));
    }

    @Test
    void 金额不接受浮点科学计数法负数或超精度() throws Exception {
        for (String value : new String[] {"-1", "1.001", "1e2", "999999999.99", "NaN"}) {
            var input = order();
            input.put("paidAmount", value);
            assertThrows(RuntimeException.class, () -> service.normalize(input, true));
        }
        var input = order();
        input.put("paidAmount", 10.01);
        assertThrows(RuntimeException.class, () -> service.normalize(input, true));
    }

    @Test
    void 重复商品身份和分摊不匹配被拒绝() throws Exception {
        var input = order();
        ((com.fasterxml.jackson.databind.node.ArrayNode) input.get("items"))
                .add(input.get("items").get(0).deepCopy());
        assertThrows(RuntimeException.class, () -> service.normalize(input, true));
        var other = order();
        other.put("paidAmount", "10.00");
        assertThrows(RuntimeException.class, () -> service.normalize(other, true));
    }

    @Test
    void 逐件尾差相加保持订单精确到分() {
        assertEquals(
                new BigDecimal("3.34"),
                PurchaseCaptureService.unitPrice(new BigDecimal("10.01"), 3, 1));
        assertEquals(
                new BigDecimal("3.34"),
                PurchaseCaptureService.unitPrice(new BigDecimal("10.01"), 3, 2));
        assertEquals(
                new BigDecimal("3.33"),
                PurchaseCaptureService.unitPrice(new BigDecimal("10.01"), 3, 3));
    }

    @Test
    void 日期必须真实且类型严格() {
        var n = json.createObjectNode().put("paidAt", "2026-02-30 12:00:00");
        assertThrows(RuntimeException.class, () -> CaptureValues.date(n, "paidAt", true));
    }

    @Test
    void 来源只能是所选平台且拒绝凭据与端口() throws Exception {
        for (String url :
                new String[] {
                    "https://evil.example/x",
                    "https://jd.com.evil.example/x",
                    "https://user:pass@details.jd.com/x",
                    "https://details.jd.com:8443/x",
                    "http://details.jd.com/x"
                }) {
            var n = order();
            n.put("sourceUrl", url);
            assertThrows(RuntimeException.class, () -> service.normalize(n, true));
        }
    }

    @Test
    void 幂等指纹忽略对象键顺序保留数组顺序() throws Exception {
        var requests = new CaptureRequests(null, json);
        assertEquals(
                requests.hash(json.readTree("{\"a\":1,\"b\":2}")),
                requests.hash(json.readTree("{\"b\":2,\"a\":1}")));
        assertNotEquals(
                requests.hash(json.readTree("[1,2]")), requests.hash(json.readTree("[2,1]")));
    }
}
