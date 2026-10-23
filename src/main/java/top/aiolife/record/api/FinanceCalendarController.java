package top.aiolife.record.api;

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

    @GetMapping("/home/sections/expense/calendar")
    public ApiResponse<FinanceCalendarVO> calendar(@RequestParam(defaultValue = "day") String granularity,
                                                 @RequestParam(required = false) String date) {
        return ApiResponse.success(service.calendar(StpUtil.getLoginIdAsLong(), granularity, date));
    }
    @GetMapping("/home/sections/income/calendar")
    public ApiResponse<FinanceCalendarVO> incomeCalendar(@RequestParam(defaultValue = "week") String granularity,
                                                       @RequestParam(required = false) String date) {
        return ApiResponse.success(service.incomeCalendar(StpUtil.getLoginIdAsLong(), granularity, date));
    }
    @GetMapping("/home/sections/finance/calendar")
    public ApiResponse<FinanceOverviewCalendarVO> overviewCalendar(@RequestParam(defaultValue = "week") String granularity,
                                                                  @RequestParam(required = false) String date) {
        return ApiResponse.success(service.overviewCalendar(StpUtil.getLoginIdAsLong(), granularity, date));
    }
}
