package top.aiolife.system.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.system.pojo.query.StorageObjectQuery;
import top.aiolife.system.pojo.vo.StoragePageVO;
import top.aiolife.system.service.StorageAdminService;

/** 对象存储管理：目录、预览、下载及删除均必须校验管理员身份。 */
@Tag(name = "存储管理")
@RestController
@RequestMapping("/system/storage")
@SaCheckRole("admin")
@RequiredArgsConstructor
public class StorageAdminController {
    private final StorageAdminService service;

    @Operation(summary = "管理员分页查询存储对象")
    @GetMapping("/objects")
    public ApiResponse<StoragePageVO> list(@Valid StorageObjectQuery query, HttpServletResponse response) throws Exception {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.success(service.list(query));
    }

    @Operation(summary = "管理员预览存储对象")
    @GetMapping("/preview")
    public void preview(@Parameter(description = "存储对象完整路径") @RequestParam String key, HttpServletResponse response) throws Exception {
        service.read(key, false, response);
    }

    @Operation(summary = "管理员下载存储对象")
    @GetMapping("/download")
    public void download(@Parameter(description = "存储对象完整路径") @RequestParam String key, HttpServletResponse response) throws Exception {
        service.read(key, true, response);
    }

    @Operation(summary = "管理员删除存储对象")
    @DeleteMapping("/object")
    public ApiResponse<Void> delete(@Parameter(description = "存储对象完整路径") @RequestParam String key) throws Exception {
        service.delete(key);
        return ApiResponse.success();
    }
}
