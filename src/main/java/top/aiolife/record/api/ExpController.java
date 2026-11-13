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
import top.aiolife.record.pojo.entity.ExpenseEntity;
import top.aiolife.record.pojo.query.ExpenseQuery;
import top.aiolife.record.pojo.req.CommonReq;
import top.aiolife.record.pojo.req.ExpenseCreateReq;
import top.aiolife.record.pojo.req.ExpenseUpdateReq;
import top.aiolife.record.pojo.vo.ExpStaticByYearVO;
import top.aiolife.record.pojo.vo.ExpenseVO;
import top.aiolife.record.service.DeviceExpenseService;
import top.aiolife.record.service.IExpenseService;

/**
 * 支出记录控制器
 *
 * @author Lys
 * @date 2025/10/03 21:01
 */
@Tag(name = "支出")
@RestController
@AllArgsConstructor
@RequestMapping("/expense")
public class ExpController {

    private final IExpenseService expenseService;

    private final DeviceExpenseService deviceExpenseService;

    @Operation(summary = "分页查询支出记录")
    @GetMapping("/query")
    public ApiResponse<PageResp<ExpenseVO>> query(@CurrentUserId String userId,
                                                  @QueryParams CommonQuery<ExpenseQuery> requestQuery) {
        long uid = IdCodec.decode(userId);
        PageResp<ExpenseVO> result = RecordApiConvertor.INSTANCE.toExpenseVOPage(expenseService.pageForUser(
                uid, requestQuery.getCondition(), requestQuery.getPage(), requestQuery.getPageSize()));
        deviceExpenseService.enrich(uid, result.getItems());
        return ApiResponse.success(result);
    }

    @Operation(summary = "新增支出记录")
    @PostMapping
    public ApiResponse<Boolean> insert(@CurrentUserId String userId,
                                       @Valid @RequestBody ExpenseCreateReq entityReq) {
        return ApiResponse.success(deviceExpenseService.createExpenses(
                IdCodec.decode(userId), List.of(entityReq)));
    }

    @Operation(summary = "更新支出记录")
    @PutMapping
    public ApiResponse<Boolean> update(@CurrentUserId String userId,
                                       @Valid @RequestBody ExpenseUpdateReq entityReq) {
        ExpenseEntity entity = RecordApiConvertor.INSTANCE.fromExpenseUpdateReq(entityReq);
        expenseService.updateForUser(IdCodec.decode(userId), entity);
        return ApiResponse.success(true);
    }

    @Operation(summary = "批量新增支出记录")
    @PostMapping("/saveBatch")
    public ApiResponse<Boolean> saveBatch(@CurrentUserId String userId,
                                          @Valid @RequestBody List<@Valid ExpenseCreateReq> requests) {
        deviceExpenseService.createExpensesWithTransactionCheck(IdCodec.decode(userId), requests);
        return ApiResponse.success();
    }

    @Operation(summary = "删除支出记录")
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@CurrentUserId String userId, @PathVariable String id) {
        return ApiResponse.success(deviceExpenseService.deleteExpenses(
                IdCodec.decode(userId), List.of(IdCodec.decode(id))));
    }

    @Operation(summary = "批量删除支出记录")
    @PostMapping("/deleteBatch")
    public ApiResponse<Boolean> deleteBatch(@CurrentUserId String userId,
                                            @Valid @RequestBody CommonReq commonReq) {
        return ApiResponse.success(deviceExpenseService.deleteExpenses(
                IdCodec.decode(userId),
                commonReq.getIdList() == null ? List.<Long>of() : IdCodec.decodeAll(commonReq.getIdList())));
    }

    @Operation(summary = "按年度和分类统计支出", description = "统计当前用户全部历史，返回分类名称和金额。")
    @GetMapping("/statisticsByYear")
    public ApiResponse<List<ExpStaticByYearVO>> statisticsByYear(@CurrentUserId String userId) {
        return ApiResponse.success(expenseService.statisticsByYear(IdCodec.decode(userId)));
    }

    @Operation(summary = "按月份和分类统计支出", description = "统计当前用户全部历史，按年月分组，不接收日期筛选。")
    @GetMapping("/statisticsByMonth")
    public ApiResponse<List<ExpStaticByYearVO>> statisticsByMonth(@CurrentUserId String userId) {
        return ApiResponse.success(expenseService.statisticsByMonth(IdCodec.decode(userId)));
    }
}
