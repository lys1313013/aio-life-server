package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.MemoEntity;
import top.aiolife.record.pojo.query.MemoQuery;
import top.aiolife.record.pojo.req.MemoCreateReq;
import top.aiolife.record.pojo.req.MemoUpdateReq;
import top.aiolife.record.pojo.vo.MemoVO;
import top.aiolife.record.service.IMemoService;

/**
 * 备忘录控制器
 *
 * @author Lys
 * @date 2025/12/07
 */
@Tag(name = "备忘")
@RestController
@AllArgsConstructor
@RequestMapping("/memo")
public class MemoController {

    private final IMemoService memoService;

    @Operation(summary = "分页查询备忘")
    @GetMapping("/query")
    public ApiResponse<PageResp<MemoVO>> query(@CurrentUserId String userId,
                                               @QueryParams CommonQuery<MemoQuery> requestQuery) {
        MemoQuery condition = requestQuery.getCondition();
        PageResp<MemoEntity> page = memoService.pageForUser(
                IdCodec.decode(userId),
                condition == null ? null : condition.getContent(),
                requestQuery.getPage(),
                requestQuery.getPageSize());
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toMemoVOPage(page));
    }

    @Operation(summary = "查询备忘详情")
    @GetMapping("/{id}")
    public ApiResponse<MemoVO> detail(@CurrentUserId String userId, @PathVariable String id) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toMemoVO(
                memoService.getOwned(IdCodec.decode(userId), IdCodec.decode(id))));
    }

    @Operation(summary = "新增备忘")
    @PostMapping
    public ApiResponse<Boolean> save(@CurrentUserId String userId,
                                     @Valid @RequestBody MemoCreateReq entityReq) {
        MemoEntity entity = RecordApiConvertor.INSTANCE.fromMemoCreateReq(entityReq);
        memoService.createForUser(IdCodec.decode(userId), entity);
        return ApiResponse.success(true);
    }

    @Operation(summary = "更新备忘")
    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(@CurrentUserId String userId, @PathVariable String id,
                                       @Valid @RequestBody MemoUpdateReq entityReq) {
        MemoEntity entity = RecordApiConvertor.INSTANCE.fromMemoUpdateReq(entityReq);
        memoService.updateForUser(IdCodec.decode(userId), IdCodec.decode(id), entity);
        return ApiResponse.success(true);
    }

    @Operation(summary = "删除备忘")
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@CurrentUserId String userId, @PathVariable String id) {
        memoService.deleteForUser(IdCodec.decode(userId), IdCodec.decode(id));
        return ApiResponse.success(true);
    }
}
