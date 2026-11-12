package top.aiolife.bankcard.api;

import io.swagger.v3.oas.annotations.Operation;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import top.aiolife.bankcard.pojo.req.*;
import top.aiolife.bankcard.pojo.vo.*;
import top.aiolife.bankcard.service.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.PageResp;
import top.aiolife.bankcard.pojo.query.BankCardCoverQuery;
import top.aiolife.record.enums.FileBizType;
import top.aiolife.record.pojo.vo.FileVO;
import top.aiolife.record.service.IFileService;

/** 公共卡面维护仅限管理员，用户选用走银行卡接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/bank-card-covers")
@SaCheckRole("admin")
public class BankCardCoverTemplateAdminController {
    private final BankCardOrderService orderService;
    @Operation(summary = "管理员调整银行卡卡面排序")
    @PutMapping("/order")
    public ApiResponse<List<BankCardOrderVO>> move(@Valid @RequestBody BankCardMoveReq req) {
        return ApiResponse.success(orderService.moveCover(StpUtil.getLoginIdAsLong(), req));
    }
    private final BankCardCoverTemplateService service;
    private final BankCardService cards;
    private final IFileService files;
    @Operation(summary = "管理员查询银行卡卡面")
    @GetMapping
    public ApiResponse<List<BankCardCoverTemplateVO>> list() { return ApiResponse.success(service.list()); }
    @Operation(summary = "管理员分页查询银行卡卡面")
    @GetMapping("/page")
    public ApiResponse<PageResp<BankCardCoverTemplateVO>> page(@Valid @QueryParams BankCardCoverQuery query) {
        return ApiResponse.success(service.page(query));
    }
    @Operation(summary = "管理员查询卡面可选银行")
    @GetMapping("/banks")
    public ApiResponse<List<BankCardVO.Bank>> banks() { return ApiResponse.success(cards.banks()); }
    @Operation(summary = "管理员新增银行卡卡面")
    @PostMapping
    public ApiResponse<BankCardCoverTemplateVO> create(@Valid @RequestBody BankCardCoverTemplateReq req) {
        return ApiResponse.success(service.save(StpUtil.getLoginIdAsLong(),null,req));
    }
    @Operation(summary = "管理员更新银行卡卡面")
    @PutMapping("/{id}")
    public ApiResponse<BankCardCoverTemplateVO> update(@PathVariable long id,@Valid @RequestBody BankCardCoverTemplateReq req) {
        return ApiResponse.success(service.save(StpUtil.getLoginIdAsLong(),id,req));
    }
    @Operation(summary = "管理员设置银行卡卡面启用状态")
    @PutMapping("/{id}/enabled")
    public ApiResponse<BankCardCoverTemplateVO> enabled(@PathVariable long id,@Valid @RequestBody BankCardCoverEnabledReq req) {
        return ApiResponse.success(service.setEnabled(StpUtil.getLoginIdAsLong(),id,req.isEnabled()));
    }
    @Operation(summary = "管理员删除银行卡卡面")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.delete(StpUtil.getLoginIdAsLong(),id); return ApiResponse.success();
    }
    @Operation(summary = "管理员上传银行卡卡面图片")
    @PostMapping("/upload")
    public ApiResponse<FileVO> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(files.upload(file,FileBizType.BANK_CARD_TEMPLATE_COVER));
    }
}
