package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.pojo.req.CbtiPersonalitySaveReq;
import top.aiolife.record.pojo.vo.CbtiPersonalityAdminVO;
import top.aiolife.record.service.ICbtiService;

/**
 * CBTI 人格管理控制器
 *
 * <p>用途：管理员对 CBTI 人格类型进行新增/编辑/删除，并上传/替换角色图片。</p>
 *
 * @author Ethan
 * @date 2026/04/19
 */
@Tag(name = "CBTI 管理")
@RestController
@RequestMapping("/cbti/admin")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class CbtiAdminController {

    private final ICbtiService cbtiService;

    @Operation(summary = "管理员查询 CBTI 人格类型")
    @GetMapping("/personalities")
    public ApiResponse<List<CbtiPersonalityAdminVO>> list() {
        return ApiResponse.success(cbtiService.listPersonalitiesForAdmin());
    }

    @Operation(summary = "管理员新增 CBTI 人格类型")
    @PostMapping("/personalities")
    public ApiResponse<CbtiPersonalityAdminVO> create(@CurrentUserId String userId,
                                                      @Valid @RequestBody CbtiPersonalitySaveReq req) {
        return ApiResponse.success(cbtiService.createPersonality(IdCodec.decode(userId), req));
    }

    @Operation(summary = "管理员更新 CBTI 人格类型")
    @PutMapping("/personalities/{id}")
    public ApiResponse<CbtiPersonalityAdminVO> update(@CurrentUserId String userId, @PathVariable String id,
                                                      @Valid @RequestBody CbtiPersonalitySaveReq req) {
        return ApiResponse.success(cbtiService.updatePersonality(
                IdCodec.decode(userId), IdCodec.decode(id), req));
    }

    @Operation(summary = "管理员删除 CBTI 人格类型")
    @DeleteMapping("/personalities/{id}")
    public ApiResponse<Boolean> delete(@CurrentUserId String userId, @PathVariable String id) {
        cbtiService.deletePersonality(IdCodec.decode(userId), IdCodec.decode(id));
        return ApiResponse.success(true);
    }

    @Operation(summary = "管理员上传 CBTI 人格图片")
    @PostMapping("/personalities/{code}/image")
    public ApiResponse<Map<String, Object>> uploadImage(@CurrentUserId String userId, @PathVariable String code,
                                                        @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(cbtiService.uploadPersonalityImage(IdCodec.decode(userId), code, file));
    }

    @Operation(summary = "管理员初始化 CBTI 人格图片")
    @PostMapping("/personalities/init-images")
    public ApiResponse<Boolean> initImages() {
        cbtiService.initImages();
        return ApiResponse.success(true);
    }
}
