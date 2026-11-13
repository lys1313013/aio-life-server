package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.record.service.PublicImageService;

import java.io.IOException;

/** 匿名公共底图入口。文件 ID 随换图变化，私人文件继续走鉴权预览。 */
@Tag(name = "公共图片")
@RestController
@RequiredArgsConstructor
public class PublicImageController {
    public static final String PATH = "/public/images/*";
    private final PublicImageService publicImageService;

    @GetMapping("/public/images/{id:[a-fA-F0-9]{32}}.{extension:png|jpg|webp|gif|bmp}")
    @Operation(summary = "读取公共图片", description = "匿名读取已发布图片。")
    @ApiResponse(responseCode = "200", description = "图片二进制内容，不是 ApiResponse JSON",
            content = @Content(mediaType = "image/*",
                    schema = @Schema(type = "string", format = "binary")))
    public void image(@PathVariable String id, @PathVariable String extension,
                      HttpServletRequest request, HttpServletResponse response) throws IOException {
        publicImageService.write(id, extension, request, response);
    }
}
