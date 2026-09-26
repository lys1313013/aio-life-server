package top.aiolife.bankcard.api;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.bankcard.pojo.req.*;
import top.aiolife.bankcard.pojo.vo.BankCardVO;
import top.aiolife.bankcard.service.BankCardService;
import top.aiolife.core.resq.ApiResponse;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bank-cards")
public class BankCardController {
    private final BankCardService service;
    @GetMapping public ApiResponse<List<BankCardVO>> list() { return ApiResponse.success(service.list(StpUtil.getLoginIdAsLong())); }
    @GetMapping("/banks") public ApiResponse<List<BankCardVO.Bank>> banks() { return ApiResponse.success(service.banks()); }
    @GetMapping("/{id}") public ApiResponse<BankCardVO> detail(@PathVariable long id) { return ApiResponse.success(service.detail(StpUtil.getLoginIdAsLong(),id)); }
    @PostMapping public ApiResponse<BankCardVO> create(@Valid @RequestBody BankCardReq req) { return ApiResponse.success(service.save(StpUtil.getLoginIdAsLong(),null,req)); }
    @PutMapping("/{id}") public ApiResponse<BankCardVO> update(@PathVariable long id,@Valid @RequestBody BankCardReq req) { return ApiResponse.success(service.save(StpUtil.getLoginIdAsLong(),id,req)); }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable long id) { service.delete(StpUtil.getLoginIdAsLong(),id); return ApiResponse.success(); }
    @PostMapping("/{id}/number") public ApiResponse<String> number(@PathVariable long id,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return ApiResponse.success(service.reveal(StpUtil.getLoginIdAsLong(),id));
    }
    @GetMapping("/tags") public ApiResponse<List<BankCardVO.Tag>> tags() { return ApiResponse.success(service.tags(StpUtil.getLoginIdAsLong())); }
    @PostMapping("/tags") public ApiResponse<BankCardVO.Tag> createTag(@Valid @RequestBody BankCardTagReq req) { return ApiResponse.success(service.saveTag(StpUtil.getLoginIdAsLong(),null,req)); }
    @PutMapping("/tags/{id}") public ApiResponse<BankCardVO.Tag> updateTag(@PathVariable long id,@Valid @RequestBody BankCardTagReq req) { return ApiResponse.success(service.saveTag(StpUtil.getLoginIdAsLong(),id,req)); }
    @DeleteMapping("/tags/{id}") public ApiResponse<Void> deleteTag(@PathVariable long id) { service.deleteTag(StpUtil.getLoginIdAsLong(),id); return ApiResponse.success(); }
}
