package top.aiolife.system.api;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.system.pojo.entity.ActivityLogEntity;
import top.aiolife.system.pojo.query.ActivityLogQuery;
import top.aiolife.system.service.ActivityLogService;

import java.io.IOException;

/** 管理员日志查询；无修改或删除入口。 */
@RestController
@RequestMapping("/system/logs")
@SaCheckRole("admin")
@RequiredArgsConstructor
public class ActivityLogController {
    private final ActivityLogService service;

    @GetMapping("/{type:operation|access}")
    public ApiResponse<PageResp<ActivityLogEntity>> list(@PathVariable String type, ActivityLogQuery query) {
        return ApiResponse.success(service.list(type.toUpperCase(java.util.Locale.ROOT), query));
    }

    @GetMapping("/{type:operation|access}/export")
    public void export(@PathVariable String type, ActivityLogQuery query, HttpServletResponse response) throws IOException {
        query.validateRange();
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + type + "-logs.csv\"");
        service.export(type.toUpperCase(java.util.Locale.ROOT), query, response.getWriter());
    }
}
