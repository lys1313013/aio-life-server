package top.aiolife.capture.api;

import cn.dev33.satoken.stp.StpUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import top.aiolife.capture.pojo.dto.CaptureSchemas;
import top.aiolife.capture.service.CaptureSecurity;
import top.aiolife.capture.service.DoubanCaptureService;
import top.aiolife.capture.service.PurchaseCaptureService;
import top.aiolife.core.exception.SecondaryLockRequiredException;
import top.aiolife.core.resq.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 浏览器预填和原业务表写入，无采购订单、支出或采购历史存储。 */
@RestController
@RequestMapping("/capture")
@RequiredArgsConstructor
public class CaptureController {
    private final DoubanCaptureService douban;
    private final PurchaseCaptureService purchase;
    private final CaptureSecurity access;

    @PostMapping("/douban/preview")
    @Operation(summary = "预览豆瓣书影并查询现有记录", description = "只解析元数据，不上传封面、不创建业务记录。")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            content =
                    @Content(
                            schema =
                                    @Schema(
                                            implementation =
                                                    CaptureSchemas.DoubanPreviewResponse.class)))
    public ApiResponse<JsonNode> doubanPreview(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            implementation =
                                                                    CaptureSchemas
                                                                            .DoubanPreviewRequest
                                                                            .class)))
                    @RequestBody
                    ObjectNode request) {
        return ApiResponse.success(douban.preview(StpUtil.getLoginIdAsLong(), request));
    }

    @PostMapping("/douban/commit")
    @Operation(
            summary = "确认保存阅读或观影",
            description =
                    "直接写入read_record/movie；更新只修改明确提供字段，需existingId和version。Redis提供24小时请求重试保护。")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            content =
                    @Content(
                            schema =
                                    @Schema(
                                            implementation =
                                                    CaptureSchemas.DoubanCommitResponse.class)))
    public ApiResponse<JsonNode> doubanCommit(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            implementation =
                                                                    CaptureSchemas
                                                                            .DoubanCommitRequest
                                                                            .class)))
                    @RequestBody
                    ObjectNode request) {
        return ApiResponse.success(douban.commit(StpUtil.getLoginIdAsLong(), request));
    }

    @PostMapping("/douban/sync")
    @Operation(
            summary = "同步豆瓣原生想看、看过或取消标记",
            description =
                    "仅在豆瓣已确认操作成功后调用；状态not_started/completed/removed，removed逻辑删除当前用户对应影片。无记录时创建原movie记录，已有记录仅同步状态、必要进度及完成时间，同状态返回unchanged。")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            content =
                    @Content(
                            schema =
                                    @Schema(
                                            implementation =
                                                    CaptureSchemas.DoubanSyncResponse.class)))
    public ApiResponse<JsonNode> doubanSync(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            implementation =
                                                                    CaptureSchemas.DoubanSyncRequest
                                                                            .class)))
                    @RequestBody
                    ObjectNode request) {
        return ApiResponse.success(douban.sync(StpUtil.getLoginIdAsLong(), request));
    }

    @PostMapping("/purchase/preview")
    @Operation(summary = "预览购物页面物品", description = "页面只是预填来源，返回draft/warnings/existingAssets，按来源订单和商品查询已保存设备／衣物，不保存订单正文。")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            content =
                    @Content(
                            schema =
                                    @Schema(
                                            implementation =
                                                    CaptureSchemas.PurchasePreviewResponse.class)))
    public ApiResponse<JsonNode> purchasePreview(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            implementation =
                                                                    CaptureSchemas.PurchaseOrder
                                                                            .class)))
                    @RequestBody
                    ObjectNode request) {
        return ApiResponse.success(purchase.preview(StpUtil.getLoginIdAsLong(), request));
    }

    @PostMapping("/purchase/commit")
    @Operation(
            summary = "直接创建设备或衣物",
            description =
                    "按选择批量写入现有device/wardrobe_item，事务保证全部成功或回滚；不新建支出；保存平台、订单ID和商品ID，同一订单商品跨设备／衣柜按已有数量查重，已有记录不覆盖。")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            content =
                    @Content(
                            schema =
                                    @Schema(
                                            implementation =
                                                    CaptureSchemas.PurchaseCommitResponse.class)))
    public ApiResponse<JsonNode> purchaseCommit(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            implementation =
                                                                    CaptureSchemas
                                                                            .PurchaseCommitRequest
                                                                            .class)))
                    @RequestBody
                    ObjectNode request) {
        return ApiResponse.success(purchase.commit(StpUtil.getLoginIdAsLong(), request));
    }

    @GetMapping("/options")
    public ApiResponse<Map<String, Object>> options() {
        long user = StpUtil.getLoginIdAsLong();
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : List.of("deviceTypes", "deviceStatuses", "wardrobeCategories"))
            out.put(key, List.of());
        try {
            access.menu(user, "/my-hub/device");
            out.put("deviceTypes", access.options(user, "device_type", false));
            out.put(
                    "deviceStatuses",
                    List.of(
                            Map.of("id", "1", "name", "使用中"),
                            Map.of("id", "2", "name", "已损坏"),
                            Map.of("id", "3", "name", "已送人"),
                            Map.of("id", "4", "name", "吃灰中")));
        } catch (SecondaryLockRequiredException ignored) {
            /* 锁定模块选项不泄露。 */
        }
        try {
            access.menu(user, "/wardrobe");
            out.put(
                    "wardrobeCategories",
                    access.categories(user).stream()
                            .map(c -> Map.of("id", c.getId().toString(), "name", c.getName()))
                            .toList());
        } catch (SecondaryLockRequiredException ignored) {
            /* 同上。 */
        }
        return ApiResponse.success(out);
    }
}
