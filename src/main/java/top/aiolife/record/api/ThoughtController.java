package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.ThoughtEntity;
import top.aiolife.record.pojo.query.ThoughtQuery;
import top.aiolife.record.pojo.req.CommonReq;
import top.aiolife.record.pojo.req.ThoughtSaveReq;
import top.aiolife.record.pojo.req.ThoughtUpdateReq;
import top.aiolife.record.pojo.vo.ThoughtRecordVO;
import top.aiolife.record.service.IThoughtService;
import top.aiolife.record.service.ThoughtCreationService;

/**
 * 闪念控制器
 *
 * @author Lys
 * @date 2025-11-16 17:01
 */
@Tag(name = "闪念 想法")
@RestController
@AllArgsConstructor
@RequestMapping("/thought")
public class ThoughtController {

    private final IThoughtService thoughtService;
    private final ThoughtCreationService thoughtCreationService;

    @GetMapping("/query")
    @Operation(summary = "分页查询闪念")
    public ApiResponse<PageResp<ThoughtRecordVO>> query(@CurrentUserId String userId,
                                                        @QueryParams CommonQuery<ThoughtQuery> requestQuery) {
        ThoughtQuery queryCondition = requestQuery.getCondition();
        ThoughtEntity condition = queryCondition == null
                ? null : RecordApiConvertor.INSTANCE.fromThoughtQuery(queryCondition);
        PageResp<ThoughtEntity> page = thoughtService.pageForUser(
                IdCodec.decode(userId), condition, requestQuery.getPage(), requestQuery.getPageSize());
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toThoughtRecordVOPage(page));
    }

    @PostMapping
    @Operation(summary = "新增闪念")
    public ApiResponse<Boolean> save(@CurrentUserId String userId, @Valid @RequestBody ThoughtSaveReq req) {
        thoughtCreationService.create(IdCodec.decode(userId), req);
        return ApiResponse.success(true);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新闪念")
    public ApiResponse<Boolean> update(@CurrentUserId String userId, @PathVariable String id,
                                       @Valid @RequestBody ThoughtUpdateReq entityReq) {
        ThoughtEntity entity = RecordApiConvertor.INSTANCE.fromThoughtUpdateReq(entityReq);
        thoughtService.updateForUser(IdCodec.decode(userId), IdCodec.decode(id), entity);
        return ApiResponse.success(true);
    }

    @PostMapping("/batchDelete")
    @Operation(summary = "批量删除闪念")
    public ApiResponse<Boolean> delete(@CurrentUserId String userId,
                                       @Valid @RequestBody CommonReq commonReq) {
        thoughtService.deleteForUser(IdCodec.decode(userId), commonReq.getIdList());
        return ApiResponse.success(true);
    }

    @GetMapping("/dashboard")
    @Operation(summary = "查询首页闪念")
    public ApiResponse<List<ThoughtRecordVO>> dashboardThoughts(@CurrentUserId String userId) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toThoughtRecordVOList(
                thoughtService.listPinnedForUser(IdCodec.decode(userId))));
    }
}
