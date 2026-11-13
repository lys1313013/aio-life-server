package top.aiolife.capture.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;

import java.util.Map;

/** 浏览器预填和原业务表写入，无采购订单、支出或采购历史存储。 */
@Tag(name = "浏览器采集")
@RestController
@RequestMapping("/capture")
@RequiredArgsConstructor
public class CaptureController {
    private final DoubanCaptureService douban;
    private final PurchaseCaptureService purchase;
    private final CaptureSecurity access;

    @PostMapping("/douban/preview")
    @Operation(summary = "预览豆瓣书影采集信息", description = "只解析元数据，不上传封面、不创建业务记录。")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            content =
                    @Content(
                            schema =
                                    @Schema(
                                            implementation =
                                                    CaptureSchemas.DoubanPreviewResponse.class)))
    public ApiResponse<JsonNode> doubanPreview(
            @CurrentUserId String userId,
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
                    @Valid @RequestBody
                    ObjectNode request) {
        return ApiResponse.success(douban.preview(IdCodec.decode(userId), request));
    }

    @PostMapping("/douban/commit")
    @Operation(
            summary = "保存采集的阅读或观影记录",
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
            @CurrentUserId String userId,
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
                    @Valid @RequestBody
                    ObjectNode request) {
        return ApiResponse.success(douban.commit(IdCodec.decode(userId), request));
    }

    @PostMapping("/douban/sync")
    @Operation(
            summary = "同步豆瓣观影标记",
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
            @CurrentUserId String userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            implementation =
                                                                    CaptureSchemas.DoubanSyncRequest
                                                                            .class)))
                    @Valid @RequestBody
                    ObjectNode request) {
        return ApiResponse.success(douban.sync(IdCodec.decode(userId), request));
    }

    @PostMapping("/purchase/preview")
    @Operation(summary = "预览购物页面采集信息", description = "页面只是预填来源，返回draft/warnings/existingAssets，按来源订单和商品查询已保存设备／衣物，不保存订单正文。")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            content =
                    @Content(
                            schema =
                                    @Schema(
                                            implementation =
                                                    CaptureSchemas.PurchasePreviewResponse.class)))
    public ApiResponse<JsonNode> purchasePreview(
            @CurrentUserId String userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            implementation =
                                                                    CaptureSchemas.PurchaseOrder
                                                                            .class)))
                    @Valid @RequestBody
                    ObjectNode request) {
        return ApiResponse.success(purchase.preview(IdCodec.decode(userId), request));
    }

    @PostMapping("/purchase/commit")
    @Operation(
            summary = "保存采集的设备或衣物",
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
            @CurrentUserId String userId,
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
                    @Valid @RequestBody
                    ObjectNode request) {
        return ApiResponse.success(purchase.commit(IdCodec.decode(userId), request));
    }

    @Operation(summary = "查询采集可选配置")
    @GetMapping("/options")
    public ApiResponse<Map<String, Object>> options(@CurrentUserId String userId) {
        return ApiResponse.success(access.options(IdCodec.decode(userId)));
    }

}
