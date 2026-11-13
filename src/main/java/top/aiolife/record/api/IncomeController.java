package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.IncomeEntity;
import top.aiolife.record.pojo.query.IncomeQuery;
import top.aiolife.record.pojo.req.IncomeCreateReq;
import top.aiolife.record.pojo.req.IncomeUpdateReq;
import top.aiolife.record.pojo.vo.IncStaticByYearVO;
import top.aiolife.record.pojo.vo.IncomeVO;
import top.aiolife.record.service.IIncomeService;

/**
 * 收入记录控制器
 *
 * @author Lys
 * @date 2025/09/14 21:09
 */
@Tag(name = "收入")
@RestController
@AllArgsConstructor
@RequestMapping("/income")
public class IncomeController {

    private final IIncomeService incomeService;

    @Operation(summary = "分页查询收入记录")
    @GetMapping("/query")
    public ApiResponse<PageResp<IncomeVO>> query(@CurrentUserId String userId,
                                                 @QueryParams CommonQuery<IncomeQuery> requestQuery) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toIncomeVOPage(incomeService.pageForUser(
                IdCodec.decode(userId), requestQuery.getCondition(),
                requestQuery.getPage(), requestQuery.getPageSize())));
    }

    @Operation(summary = "新增收入记录")
    @PostMapping
    public ApiResponse<Boolean> add(@CurrentUserId String userId,
                                    @Valid @RequestBody IncomeCreateReq entityReq) {
        IncomeEntity entity = RecordApiConvertor.INSTANCE.fromIncomeCreateReq(entityReq);
        return ApiResponse.success(incomeService.createForUser(IdCodec.decode(userId), entity));
    }

    @Operation(summary = "更新收入记录")
    @PutMapping("/{incomeId}")
    public ApiResponse<Boolean> update(@CurrentUserId String userId, @PathVariable String incomeId,
                                       @Valid @RequestBody IncomeUpdateReq entityReq) {
        IncomeEntity entity = RecordApiConvertor.INSTANCE.fromIncomeUpdateReq(entityReq);
        return ApiResponse.success(incomeService.updateForUser(
                IdCodec.decode(userId), IdCodec.decode(incomeId), entity));
    }

    @Operation(summary = "删除收入记录")
    @DeleteMapping("/{incomeId}")
    public ApiResponse<Boolean> delete(@CurrentUserId String userId, @PathVariable String incomeId) {
        return ApiResponse.success(incomeService.deleteForUser(
                IdCodec.decode(userId), IdCodec.decode(incomeId)));
    }

    @Operation(summary = "按年度和分类统计收入", description = "统计当前用户全部历史，返回分类名称和金额。")
    @GetMapping("/statisticsByYear")
    public ApiResponse<List<IncStaticByYearVO>> statisticsByYear(@CurrentUserId String userId) {
        return ApiResponse.success(incomeService.statisticsByYear(IdCodec.decode(userId)));
    }

    @Operation(summary = "按月份和分类统计收入", description = "统计当前用户全部历史，按年月分组，不接收日期筛选。")
    @GetMapping("/statisticsByMonth")
    public ApiResponse<List<IncStaticByYearVO>> statisticsByMonth(@CurrentUserId String userId) {
        return ApiResponse.success(incomeService.statisticsByMonth(IdCodec.decode(userId)));
    }
}
