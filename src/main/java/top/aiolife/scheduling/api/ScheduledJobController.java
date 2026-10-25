package top.aiolife.scheduling.api;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.scheduling.pojo.req.ScheduledJobUpdateReq;
import top.aiolife.scheduling.pojo.vo.ScheduledJobVO;
import top.aiolife.scheduling.pojo.vo.ScheduledJobLogVO;
import top.aiolife.scheduling.service.ScheduledJobService;
import top.aiolife.scheduling.service.ScheduledJobRuntime;

@RestController
@RequestMapping("/system/scheduled-jobs")
@SaCheckRole("admin")
@Validated
@RequiredArgsConstructor
public class ScheduledJobController {
    private final ScheduledJobService service;
    private final ScheduledJobRuntime runtime;

    @GetMapping
    public ApiResponse<List<ScheduledJobVO>> list() { return ApiResponse.success(service.list()); }

    @PutMapping("/{id}")
    public ApiResponse<ScheduledJobVO> update(@PathVariable @NotNull Long id, @Valid @RequestBody ScheduledJobUpdateReq req) {
        return ApiResponse.success(service.update(id, req, StpUtil.getLoginIdAsLong()));
    }

    @PostMapping("/{id}/run")
    public ApiResponse<Void> run(@PathVariable @NotNull Long id) {
        runtime.runNow(id, StpUtil.getLoginIdAsLong());
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/logs")
    public ApiResponse<PageResp<ScheduledJobLogVO>> logs(@PathVariable @NotNull Long id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(service.logs(id, page, pageSize));
    }
}
