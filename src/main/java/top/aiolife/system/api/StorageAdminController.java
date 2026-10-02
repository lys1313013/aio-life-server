package top.aiolife.system.api;

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
@RestController
@RequestMapping("/system/storage")
@SaCheckRole("admin")
@RequiredArgsConstructor
public class StorageAdminController {
    private final StorageAdminService service;

    @GetMapping("/objects")
    public ApiResponse<StoragePageVO> list(@Valid StorageObjectQuery query, HttpServletResponse response) throws Exception {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.success(service.list(query));
    }

    @GetMapping("/preview")
    public void preview(@RequestParam String key, HttpServletResponse response) throws Exception {
        service.read(key, false, response);
    }

    @GetMapping("/download")
    public void download(@RequestParam String key, HttpServletResponse response) throws Exception {
        service.read(key, true, response);
    }

    @DeleteMapping("/object")
    public ApiResponse<Void> delete(@RequestParam String key) throws Exception {
        service.delete(key);
        return ApiResponse.success();
    }
}
