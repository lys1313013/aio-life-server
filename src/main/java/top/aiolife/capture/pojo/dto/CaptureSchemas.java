package top.aiolife.capture.pojo.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.util.List;

/** OpenAPI 的完整采集契约。运行时采用字段白名单解析，以区别未提供与显式 null。 */
public final class CaptureSchemas {
    private CaptureSchemas() {}

    @Data
    public static class DoubanMetadata {
        private String title;
        private String author;
        private String director;

        @Schema(description = "书籍为1；影视1电影/2剧集/3动漫/4纪录片/5其他")
        private Integer type;

        private Integer totalProgress;
        private String coverImgUrl;
    }

    @Data
    public static class MediaFields {
        @Schema(maxLength = 255)
        private String title;

        @Schema(maxLength = 100)
        private String author;

        @Schema(maxLength = 100)
        private String director;

        @Schema(description = "书籍1；影视1..5")
        private Integer type;

        @Schema(description = "0或null表示尚未记录总进度")
        private Integer totalProgress;

        private Integer currentProgress;

        @Schema(allowableValues = {"not_started", "in_progress", "completed", "on_hold"})
        private String status;

        @Schema(maxLength = 1000)
        private String remark;

        @Schema(
                description = "个人影视评分1..5；null清空，不是豆瓣公共评分",
                minimum = "1",
                maximum = "5",
                nullable = true)
        private Integer rating;

        @Schema(description = "本人上传图片ID，空字符串清空；省略保留原图", maxLength = 32)
        private String fileId;
    }

    @Data
    public static class DoubanPreviewRequest {
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "https://book.douban.com/subject/12345/")
        private String url;

        private DoubanMetadata metadata;
    }

    @Data
    public static class DoubanCommitRequest {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String url;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100)
        private String idempotencyKey;

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"create", "update"})
        private String action;

        @Schema(description = "更新必填，preview.existing.id")
        private String existingId;

        @Schema(description = "更新必填，preview.existing.version原样传回")
        private String version;

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "更新只修改显式提供的属性；创建需title/type")
        private MediaFields fields;
    }

    @Data
    public static class DoubanDraft extends DoubanMetadata {
        private String url;
        private String mediaType;
        private String subjectId;
        private String status;
        private Integer currentProgress;
    }

    @Data
    public static class ExistingMedia {
        private String id;
        private String version;
        private MediaFields fields;
    }

    @Data
    public static class DoubanPreview {
        private DoubanDraft draft;

        @Schema(nullable = true)
        private ExistingMedia existing;

        private List<String> warnings;
    }

    @Data
    public static class DoubanResult {
        private String id;
        private String mediaType;

        @Schema(allowableValues = {"created", "updated", "existing"})
        private String operation;

        private String version;
        private String replayExpiresAt;
        private String retryStatus;
        private List<String> warnings;
    }

    @Data
    public static class MovieSyncMetadata {
        @Schema(maxLength = 255)
        private String title;

        @Schema(maxLength = 100)
        private String director;

        @Schema(description = "1电影/2剧集/3动漫/4纪录片/5其他")
        private Integer type;

        @Schema(minimum = "0", maximum = "1000000")
        private Integer totalProgress;

        @Schema(description = "仅接受既有安全上传流程允许的豆瓣图片来源", maxLength = 2048)
        private String coverImgUrl;
    }

    @Data
    public static class DoubanSyncRequest {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "豆瓣movie条目URL")
        private String url;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100)
        private String idempotencyKey;

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"not_started", "completed", "removed"})
        private String status;

        @Schema(description = "只供新建影片预填，不覆盖现有个人记录")
        private MovieSyncMetadata metadata;
    }

    @Data
    public static class DoubanSyncResult {
        @Schema(nullable = true, description = "业务记录ID；取消时原本无记录为null")
        private String id;

        @Schema(allowableValues = {"movie"})
        private String mediaType;

        @Schema(allowableValues = {"created", "updated", "unchanged", "deleted", "absent"})
        private String operation;

        @Schema(allowableValues = {"not_started", "completed", "removed"})
        private String status;

        private String replayExpiresAt;
        private String retryStatus;
        private List<String> warnings;
    }

    @Data
    public static class DoubanSyncResponse {
        private Integer code;
        private String message;
        private DoubanSyncResult data;
    }

    @Data
    public static class PurchaseItem {
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = 128,
                description = "当前请求内唯一商品行标识，仅用于页面映射，不持久化")
        private String sourceItemId;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
        private String title;

        @Schema(maxLength = 100, description = "来源商品ID，选中保存的商品必填，保持字符串")
        private String sku;
        private String spec;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", maximum = "100")
        private Integer quantity;

        @Schema(
                description = "商品行全部件数实付分摊总额，含优惠运费；commit必填",
                example = "100.01",
                pattern = "^(0|[1-9][0-9]{0,7})(\\.[0-9]{1,2})?$")
        private String paidAmount;

        private String imageUrl;
    }

    @Data
    public static class PurchaseOrder {
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"jd", "taobao"})
        private String platform;

        @Schema(maxLength = 100, description = "预览可空、提交必填；原始订单ID，与平台共同定位来源")
        private String orderNo;

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "对应平台https订单地址，服务端剔除PassKey等私密参数")
        private String sourceUrl;

        private String shopName;

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {
                    "paid",
                    "completed",
                    "unpaid",
                    "refunded",
                    "partial_refund",
                    "presale",
                    "unknown"
                })
        private String orderStatus;

        @Schema(description = "订单实付元，commit必填；商品分摊总和须相等", example = "100.01")
        private String paidAmount;

        @Schema(description = "可选，用于预填物品购买日期；未提供则使用下单时间", example = "2026-10-22 12:30:00")
        private String paidAt;

        @Schema(example = "2026-10-22 12:00:00")
        private String orderedAt;

        @Schema(description = "元，十进制字符串")
        private String freight;

        @Schema(description = "元，十进制字符串")
        private String discount;

        @ArraySchema(
                minItems = 1,
                maxItems = 100,
                schema = @Schema(implementation = PurchaseItem.class))
        private List<PurchaseItem> items;
    }

    @Data
    public static class AssetFields {
        @Schema(description = "设备最多255字，衣物最多100字")
        private String name;

        @Schema(description = "本人上传图片ID，业务类型device或wardrobe_item")
        private String fileId;

        @Schema(description = "设备类型，options.deviceTypes.id")
        private String type;

        @Schema(description = "设备状态1使用中/2已损坏/3已送人/4吃灰中")
        private String status;

        private String categoryId;
        private String color;
        private String brand;

        @Schema(maxLength = 20)
        private String size;

        private String memo;
        private String remark;
    }

    @Data
    public static class AssetAction {
        @Schema(description = "可选，本次保留的已保存物品ID；必须属于当前用户、平台、订单和商品，删除后拒绝重建")
        private String existingId;

        @Schema(description = "当前请求内商品行标识，不持久化")
        private String sourceItemId;

        @Schema(description = "第几件，1..quantity")
        private Integer unitIndex;

        @Schema(allowableValues = {"device", "wardrobe"})
        private String kind;

        private AssetFields fields;
    }

    @Data
    public static class PurchaseCommitRequest {
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = 100,
                description = "同一份请求24小时内重试保持同一键；不是永久商品身份")
        private String idempotencyKey;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "页面预填草稿，不保存订单")
        private PurchaseOrder order;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "必须显式true，确认物品购买价格")
        private Boolean amountConfirmed;

        @ArraySchema(
                minItems = 1,
                maxItems = 500,
                schema = @Schema(implementation = AssetAction.class))
        private List<AssetAction> assets;
    }

    @Data
    public static class AssetLink {
        private String sourceItemId;
        private Integer unitIndex;
        private String kind;
        private String id;
    }

    @Data
    public static class PurchaseResult {
        private List<AssetLink> assets;

        @Schema(allowableValues = {"created", "existing", "mixed"})
        private String operation;

        private Integer createdCount;
        private Integer existingCount;

        @Schema(description = "短期结果缓存过期时间（UTC ISO8601），不是永久去重保证")
        private String replayExpiresAt;

        @Schema(description = "仅缓存结果未确认时返回unconfirmed；业务已保存，请核对返回的ID")
        private String retryStatus;

        private List<String> warnings;
    }

    @Data
    public static class PurchasePreview {
        private PurchaseOrder draft;
        private List<AssetLink> existingAssets;
        private List<String> warnings;
    }

    @Data
    public static class DoubanPreviewResponse {
        private Integer code;
        private String message;
        private DoubanPreview data;
    }

    @Data
    public static class DoubanCommitResponse {
        private Integer code;
        private String message;
        private DoubanResult data;
    }

    @Data
    public static class PurchasePreviewResponse {
        private Integer code;
        private String message;
        private PurchasePreview data;
    }

    @Data
    public static class PurchaseCommitResponse {
        private Integer code;
        private String message;
        private PurchaseResult data;
    }
}
