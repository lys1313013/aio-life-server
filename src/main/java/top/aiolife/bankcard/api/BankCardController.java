package top.aiolife.bankcard.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.bankcard.pojo.req.*;
import top.aiolife.bankcard.pojo.vo.BankCardCoverTemplateVO;
import top.aiolife.bankcard.pojo.vo.BankCardVO;
import top.aiolife.bankcard.service.BankCardCoverTemplateService;
import top.aiolife.bankcard.service.BankCardService;
import top.aiolife.bankcard.service.BankCardOrderService;
import top.aiolife.bankcard.pojo.vo.BankCardOrderVO;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import java.util.List;

/**
 * 银行卡控制器
 *
 * @author Lys
 * @date 2026/11/13
 */
@Tag(name = "银行卡")
@RestController
@RequiredArgsConstructor
@RequestMapping("/bank-cards")
public class BankCardController {
    private final BankCardOrderService orderService;
    private final BankCardService service;
    private final BankCardCoverTemplateService covers;

    @Operation(summary = "调整银行卡排序")
    @PutMapping("/order")
    public ApiResponse<List<BankCardOrderVO>> move(@CurrentUserId String userId,
                                                   @Valid @RequestBody BankCardMoveReq req) {
        return ApiResponse.success(orderService.moveCard(IdCodec.decode(userId), req));
    }

    @Operation(summary = "查询银行卡可选卡面")
    @GetMapping("/cover-templates")
    public ApiResponse<List<BankCardCoverTemplateVO.Option>> covers(
            @RequestParam String bankId,
            @Parameter(description = "卡片类型：debit 储蓄卡、credit 信用卡") @RequestParam String cardType) {
        return ApiResponse.success(covers.options(IdCodec.decode(bankId), cardType));
    }

    @Operation(summary = "查询我的银行卡")
    @GetMapping
    public ApiResponse<List<BankCardVO>> list(@CurrentUserId String userId) {
        return ApiResponse.success(service.list(IdCodec.decode(userId)));
    }

    @Operation(summary = "查询银行字典")
    @GetMapping("/banks")
    public ApiResponse<List<BankCardVO.Bank>> banks() {
        return ApiResponse.success(service.banks());
    }

    @Operation(summary = "查询银行卡详情")
    @GetMapping("/{id}")
    public ApiResponse<BankCardVO> detail(@CurrentUserId String userId, @PathVariable String id) {
        return ApiResponse.success(service.detail(IdCodec.decode(userId), IdCodec.decode(id)));
    }

    @Operation(summary = "新增银行卡")
    @PostMapping
    public ApiResponse<BankCardVO> create(@CurrentUserId String userId, @Valid @RequestBody BankCardReq req) {
        return ApiResponse.success(service.save(IdCodec.decode(userId), null, req));
    }

    @Operation(summary = "更新银行卡")
    @PutMapping("/{id}")
    public ApiResponse<BankCardVO> update(@CurrentUserId String userId, @PathVariable String id,
                                          @Valid @RequestBody BankCardReq req) {
        return ApiResponse.success(service.save(IdCodec.decode(userId), IdCodec.decode(id), req));
    }

    @Operation(summary = "删除银行卡")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@CurrentUserId String userId, @PathVariable String id) {
        service.delete(IdCodec.decode(userId), IdCodec.decode(id));
        return ApiResponse.success();
    }

    @Operation(summary = "读取银行卡完整卡号")
    @PostMapping("/{id}/number")
    public ApiResponse<String> number(@CurrentUserId String userId, @PathVariable String id,
                                      HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.success(service.reveal(IdCodec.decode(userId), IdCodec.decode(id)));
    }

    @Operation(summary = "查询银行卡标签")
    @GetMapping("/tags")
    public ApiResponse<List<BankCardVO.Tag>> tags(@CurrentUserId String userId) {
        return ApiResponse.success(service.tags(IdCodec.decode(userId)));
    }

    @Operation(summary = "新增银行卡标签")
    @PostMapping("/tags")
    public ApiResponse<BankCardVO.Tag> createTag(@CurrentUserId String userId,
                                                 @Valid @RequestBody BankCardTagReq req) {
        return ApiResponse.success(service.saveTag(IdCodec.decode(userId), null, req));
    }

    @Operation(summary = "更新银行卡标签")
    @PutMapping("/tags/{id}")
    public ApiResponse<BankCardVO.Tag> updateTag(@CurrentUserId String userId, @PathVariable String id,
                                                 @Valid @RequestBody BankCardTagReq req) {
        return ApiResponse.success(service.saveTag(IdCodec.decode(userId), IdCodec.decode(id), req));
    }

    @Operation(summary = "删除银行卡标签")
    @DeleteMapping("/tags/{id}")
    public ApiResponse<Void> deleteTag(@CurrentUserId String userId, @PathVariable String id) {
        service.deleteTag(IdCodec.decode(userId), IdCodec.decode(id));
        return ApiResponse.success();
    }
}
