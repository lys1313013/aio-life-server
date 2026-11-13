package top.aiolife.membership.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.membership.convertor.MembershipApiConvertor;
import top.aiolife.membership.pojo.req.MembershipCreateReq;
import top.aiolife.membership.pojo.req.MembershipReq;
import top.aiolife.membership.pojo.vo.MembershipStatsVO;
import top.aiolife.membership.pojo.vo.MembershipVO;
import top.aiolife.membership.service.MembershipRecordWriteService;

/**
 * 会员记录控制器
 *
 * @author Lys
 * @date 2026/08/06
 */
@Tag(name = "会员订阅")
@RestController
@AllArgsConstructor
@RequestMapping("/membership")
public class MembershipController {

    private final MembershipRecordWriteService recordWriter;

    @Operation(summary = "查询我的会员订阅")
    @GetMapping("/list")
    public ApiResponse<List<MembershipVO>> list(@CurrentUserId String userId) {
        return ApiResponse.success(recordWriter.listVOs(IdCodec.decode(userId)));
    }

    @Operation(summary = "查询会员订阅统计")
    @GetMapping("/stats")
    public ApiResponse<MembershipStatsVO> stats(@CurrentUserId String userId) {
        return ApiResponse.success(recordWriter.stats(IdCodec.decode(userId)));
    }

    @Operation(summary = "查询会员订阅详情")
    @GetMapping("/{id}")
    public ApiResponse<MembershipVO> detail(@CurrentUserId String userId, @PathVariable String id) {
        return ApiResponse.success(recordWriter.detailVO(IdCodec.decode(userId), IdCodec.decode(id)));
    }

    @Operation(summary = "新增会员订阅")
    @PostMapping
    public ApiResponse<MembershipVO> create(@CurrentUserId String userId,
                                            @Valid @RequestBody MembershipCreateReq request) {
        MembershipReq req = MembershipApiConvertor.INSTANCE.fromMembershipCreateReq(request);
        return ApiResponse.success(recordWriter.createVO(IdCodec.decode(userId), req));
    }

    @Operation(summary = "更新会员订阅")
    @PutMapping
    public ApiResponse<MembershipVO> update(@CurrentUserId String userId,
                                            @Valid @RequestBody MembershipReq req) {
        return ApiResponse.success(recordWriter.updateVO(IdCodec.decode(userId), req));
    }

    @Operation(summary = "删除会员订阅")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@CurrentUserId String userId, @PathVariable String id) {
        recordWriter.delete(IdCodec.decode(userId), IdCodec.decode(id));
        return ApiResponse.success();
    }
}
