package top.aiolife.system.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.system.convertor.SystemApiConvertor;
import top.aiolife.system.pojo.query.ActivityLogQuery;
import top.aiolife.system.pojo.vo.ActivityLogDetailVO;
import top.aiolife.system.pojo.vo.ActivityLogVO;
import top.aiolife.system.service.ActivityLogService;

/**
 * 管理员日志查询；无修改或删除入口。
 *
 * @author Lys
 * @date 2026/11/13
 */
@Tag(name = "操作日志")
@RestController
@RequestMapping("/system/logs")
@SaCheckRole("admin")
@RequiredArgsConstructor
public class ActivityLogController {
    private final ActivityLogService service;

    @Operation(summary = "管理员分页查询操作或访问日志")
    @GetMapping("/{type:operation|access}")
    public ApiResponse<PageResp<ActivityLogVO>> list(
            @Parameter(description = "日志类型：operation 操作、access 访问") @PathVariable String type,
            ActivityLogQuery query) {
        return ApiResponse.success(SystemApiConvertor.INSTANCE.toActivityLogVOPage(service.list(type, query)));
    }

    @Operation(summary = "管理员查询操作日志详情")
    @GetMapping("/operation/{id}")
    public ApiResponse<ActivityLogDetailVO> detail(@PathVariable String id) {
        return ApiResponse.success(SystemApiConvertor.INSTANCE.toActivityLogDetailVO(service.detail(IdCodec.decode(id))));
    }

    @Operation(summary = "管理员导出操作或访问日志")
    @GetMapping("/{type:operation|access}/export")
    public void export(@Parameter(description = "日志类型：operation 操作、access 访问") @PathVariable String type,
                       ActivityLogQuery query, HttpServletResponse response) throws IOException {
        query.validateRange();
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + type + "-logs.csv\"");
        service.export(type, query, response.getWriter());
    }
}
