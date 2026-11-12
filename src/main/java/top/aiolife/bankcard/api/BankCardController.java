package top.aiolife.bankcard.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import cn.dev33.satoken.stp.StpUtil;
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
import top.aiolife.core.resq.ApiResponse;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bank-cards")
public class BankCardController {
    private final BankCardOrderService orderService;
    @Operation(summary = "调整银行卡排序")
    @PutMapping("/order")
    public ApiResponse<List<BankCardOrderVO>> move(@Valid @RequestBody BankCardMoveReq req) {
        return ApiResponse.success(orderService.moveCard(StpUtil.getLoginIdAsLong(), req));
    }
    private final BankCardService service;
    private final BankCardCoverTemplateService covers;
    @Operation(summary = "查询银行卡可选卡面")
    @GetMapping("/cover-templates")
    public ApiResponse<List<BankCardCoverTemplateVO.Option>> covers(@RequestParam long bankId, @Parameter(description = "卡片类型：debit 储蓄卡、credit 信用卡") @RequestParam String cardType) {
        return ApiResponse.success(covers.options(bankId,cardType));
    }
    @Operation(summary = "查询我的银行卡")
    @GetMapping public ApiResponse<List<BankCardVO>> list() { return ApiResponse.success(service.list(StpUtil.getLoginIdAsLong())); }
    @Operation(summary = "查询银行字典")
    @GetMapping("/banks") public ApiResponse<List<BankCardVO.Bank>> banks() { return ApiResponse.success(service.banks()); }
    @Operation(summary = "查询银行卡详情")
    @GetMapping("/{id}") public ApiResponse<BankCardVO> detail(@PathVariable long id) { return ApiResponse.success(service.detail(StpUtil.getLoginIdAsLong(),id)); }
    @Operation(summary = "新增银行卡")
    @PostMapping public ApiResponse<BankCardVO> create(@Valid @RequestBody BankCardReq req) { return ApiResponse.success(service.save(StpUtil.getLoginIdAsLong(),null,req)); }
    @Operation(summary = "更新银行卡")
    @PutMapping("/{id}") public ApiResponse<BankCardVO> update(@PathVariable long id,@Valid @RequestBody BankCardReq req) { return ApiResponse.success(service.save(StpUtil.getLoginIdAsLong(),id,req)); }
    @Operation(summary = "删除银行卡")
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable long id) { service.delete(StpUtil.getLoginIdAsLong(),id); return ApiResponse.success(); }
    @Operation(summary = "读取银行卡完整卡号")
    @PostMapping("/{id}/number") public ApiResponse<String> number(@PathVariable long id,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return ApiResponse.success(service.reveal(StpUtil.getLoginIdAsLong(),id));
    }
    @Operation(summary = "查询银行卡标签")
    @GetMapping("/tags") public ApiResponse<List<BankCardVO.Tag>> tags() { return ApiResponse.success(service.tags(StpUtil.getLoginIdAsLong())); }
    @Operation(summary = "新增银行卡标签")
    @PostMapping("/tags") public ApiResponse<BankCardVO.Tag> createTag(@Valid @RequestBody BankCardTagReq req) { return ApiResponse.success(service.saveTag(StpUtil.getLoginIdAsLong(),null,req)); }
    @Operation(summary = "更新银行卡标签")
    @PutMapping("/tags/{id}") public ApiResponse<BankCardVO.Tag> updateTag(@PathVariable long id,@Valid @RequestBody BankCardTagReq req) { return ApiResponse.success(service.saveTag(StpUtil.getLoginIdAsLong(),id,req)); }
    @Operation(summary = "删除银行卡标签")
    @DeleteMapping("/tags/{id}") public ApiResponse<Void> deleteTag(@PathVariable long id) { service.deleteTag(StpUtil.getLoginIdAsLong(),id); return ApiResponse.success(); }
}
