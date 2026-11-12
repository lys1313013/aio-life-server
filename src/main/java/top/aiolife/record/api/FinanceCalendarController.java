package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.pojo.vo.FinanceCalendarVO;
import top.aiolife.record.pojo.vo.FinanceOverviewCalendarVO;
import top.aiolife.record.service.FinanceCalendarService;

@RestController
@RequiredArgsConstructor
public class FinanceCalendarController {
    private final FinanceCalendarService service;

    @Operation(summary = "查询财务支出日历")
    @GetMapping("/home/sections/expense/calendar")
    public ApiResponse<FinanceCalendarVO> calendar(
            @Parameter(description = "展示粒度：day、week、month、year")
            @RequestParam(defaultValue = "day") String granularity,
            @Parameter(description = "day/week 用 yyyy-MM，month 用 yyyy，省略取当前；year 不传，查询全部历史。")
            @RequestParam(required = false) String date) {
        return ApiResponse.success(service.calendar(StpUtil.getLoginIdAsLong(), granularity, date));
    }
    @Operation(summary = "查询财务收入日历")
    @GetMapping("/home/sections/income/calendar")
    public ApiResponse<FinanceCalendarVO> incomeCalendar(
            @Parameter(description = "展示粒度：week、month、year")
            @RequestParam(defaultValue = "week") String granularity,
            @Parameter(description = "week 用 yyyy-MM，month 用 yyyy，省略取当前；year 不传，查询全部历史。")
            @RequestParam(required = false) String date) {
        return ApiResponse.success(service.incomeCalendar(StpUtil.getLoginIdAsLong(), granularity, date));
    }
    @Operation(summary = "查询财务收支概览", description = "结余为期间收入减支出，不代表账户余额。")
    @GetMapping("/home/sections/finance/calendar")
    public ApiResponse<FinanceOverviewCalendarVO> overviewCalendar(
            @Parameter(description = "展示粒度：week、month、year")
            @RequestParam(defaultValue = "week") String granularity,
            @Parameter(description = "week 用 yyyy-MM，month 用 yyyy，省略取当前；year 不传，查询全部历史。")
            @RequestParam(required = false) String date) {
        return ApiResponse.success(service.overviewCalendar(StpUtil.getLoginIdAsLong(), granularity, date));
    }
}
