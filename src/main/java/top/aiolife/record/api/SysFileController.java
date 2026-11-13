package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.pojo.vo.FileVO;
import top.aiolife.record.service.FileAccessService;

/**
 * 文件上传、预览与下载控制器。
 *
 * @author Lys
 */
@Tag(name = "文件")
@RestController
@RequiredArgsConstructor
@RequestMapping("/file")
public class SysFileController {

    private final FileAccessService fileAccessService;

    /**
     * 统一文件上传入口。
     */
    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public ApiResponse<FileVO> upload(@CurrentUserId String userId,
                                      @RequestParam("file") MultipartFile file,
                                      @RequestParam("bizType") String bizType) {
        return ApiResponse.success(fileAccessService.upload(file, bizType, IdCodec.decode(userId)));
    }

    @GetMapping("/preview/{id:[a-fA-F0-9]{32}}")
    @Operation(summary = "预览文件")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "文件内容，Content-Type 取决于文件类型；不是 ApiResponse JSON",
            content = @Content(mediaType = "*/*", schema = @Schema(type = "string", format = "binary")))
    public void preview(@Parameter(description = "上传响应中的文件 ID", schema = @Schema(pattern = "^[a-fA-F0-9]{32}$"))
                        @PathVariable("id") String id, HttpServletResponse response) {
        fileAccessService.write(id, false, response);
    }

    @GetMapping("/download/{id:[a-fA-F0-9]{32}}")
    @Operation(summary = "下载文件")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "文件内容，Content-Disposition 为 attachment；不是 ApiResponse JSON",
            content = @Content(mediaType = "*/*", schema = @Schema(type = "string", format = "binary")))
    public void download(@Parameter(description = "上传响应中的文件 ID", schema = @Schema(pattern = "^[a-fA-F0-9]{32}$"))
                         @PathVariable("id") String id, HttpServletResponse response) {
        fileAccessService.write(id, true, response);
    }
}
