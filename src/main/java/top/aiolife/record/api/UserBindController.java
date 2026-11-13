package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.UserBindEntity;
import top.aiolife.record.pojo.req.UserBindCreateReq;
import top.aiolife.record.pojo.req.UserBindUpdateReq;
import top.aiolife.record.pojo.vo.DoubanAccountVO;
import top.aiolife.record.pojo.vo.UserBindVO;
import top.aiolife.record.service.IUserBindService;

/**
 * 用户第三方账号绑定接口
 */
@Tag(name = "第三方账号绑定")
@RestController
@RequiredArgsConstructor
@RequestMapping("/userbinds")
public class UserBindController {

    private final IUserBindService userBindService;

    /**
     * 获取当前用户的绑定列表
     */
    @Operation(summary = "查询第三方账号绑定")
    @GetMapping("/list")
    public ApiResponse<List<UserBindVO>> list(@CurrentUserId String userId,
                                              @RequestParam(required = false) Boolean includeToken) {
        List<UserBindEntity> list = userBindService.listForUser(IdCodec.decode(userId), includeToken);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toUserBindVOList(list));
    }

    /**
     * 根据豆瓣账号 ID 查询公开主页昵称，仅用于辅助用户确认账号。
     */
    @Operation(summary = "查询豆瓣账号公开昵称")
    @GetMapping("/douban/verify")
    public ApiResponse<DoubanAccountVO> verifyDoubanAccount(@RequestParam String accountId) {
        return ApiResponse.success(userBindService.verifyDoubanAccount(accountId));
    }

    /**
     * 新增绑定
     */
    @Operation(summary = "新增第三方账号绑定")
    @PostMapping
    public ApiResponse<Boolean> add(@CurrentUserId String userId,
                                    @Valid @RequestBody UserBindCreateReq userBindEntityReq) {
        UserBindEntity userBindEntity = RecordApiConvertor.INSTANCE.fromUserBindCreateReq(userBindEntityReq);
        return ApiResponse.success(userBindService.createForUser(IdCodec.decode(userId), userBindEntity));
    }

    /**
     * 更新绑定
     */
    @Operation(summary = "更新第三方账号绑定")
    @PutMapping
    public ApiResponse<Boolean> update(@CurrentUserId String userId,
                                       @Valid @RequestBody UserBindUpdateReq userBindEntityReq) {
        UserBindEntity userBindEntity = RecordApiConvertor.INSTANCE.fromUserBindUpdateReq(userBindEntityReq);
        return ApiResponse.success(userBindService.updateForUser(IdCodec.decode(userId), userBindEntity));
    }

    /**
     * 删除绑定
     */
    @Operation(summary = "删除第三方账号绑定")
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@CurrentUserId String userId, @PathVariable String id) {
        return ApiResponse.success(userBindService.deleteForUser(
                IdCodec.decode(userId), IdCodec.decode(id)));
    }
}
