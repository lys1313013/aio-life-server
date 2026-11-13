package top.aiolife.scheduling.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.validation.Valid;
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
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.scheduling.pojo.req.ScheduledJobUpdateReq;
import top.aiolife.scheduling.pojo.vo.ScheduledJobVO;
import top.aiolife.scheduling.pojo.vo.ScheduledJobLogVO;
import top.aiolife.scheduling.service.ScheduledJobService;
import top.aiolife.scheduling.service.ScheduledJobRuntime;

/**
 * 定时任务管理控制器。
 *
 * @author Lys
 * @date 2026/11/13
 */
@Tag(name = "定时任务")
@RestController
@RequestMapping("/system/scheduled-jobs")
@SaCheckRole("admin")
@Validated
@RequiredArgsConstructor
public class ScheduledJobController {
    private final ScheduledJobService service;
    private final ScheduledJobRuntime runtime;

    @Operation(summary = "管理员查询定时任务")
    @GetMapping
    public ApiResponse<List<ScheduledJobVO>> list() {
        return ApiResponse.success(service.list());
    }

    @Operation(summary = "管理员更新定时任务")
    @PutMapping("/{id}")
    public ApiResponse<ScheduledJobVO> update(@CurrentUserId String userId, @PathVariable String id,
                                              @Valid @RequestBody ScheduledJobUpdateReq req) {
        return ApiResponse.success(service.update(IdCodec.decode(id), req, IdCodec.decode(userId)));
    }

    @Operation(summary = "管理员立即执行定时任务")
    @PostMapping("/{id}/run")
    public ApiResponse<Void> run(@CurrentUserId String userId, @PathVariable String id) {
        runtime.runNow(IdCodec.decode(id), IdCodec.decode(userId));
        return ApiResponse.success(null);
    }

    @Operation(summary = "管理员分页查询定时任务日志")
    @GetMapping("/{id}/logs")
    public ApiResponse<PageResp<ScheduledJobLogVO>> logs(@PathVariable String id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(service.logs(IdCodec.decode(id), page, pageSize));
    }
}
