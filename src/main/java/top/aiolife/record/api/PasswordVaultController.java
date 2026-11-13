package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.PasswordVaultEntity;
import top.aiolife.record.pojo.req.PasswordVaultCreateReq;
import top.aiolife.record.pojo.req.PasswordVaultUpdateReq;
import top.aiolife.record.pojo.vo.PasswordVaultVO;
import top.aiolife.record.service.IPasswordVaultService;

/**
 * 密码库控制器。
 * 注意：加解密全部在前端完成（SM4-GCM，密钥由用户主密码经 PBKDF2 派生），
 * 本控制器仅做密文的透传存取，后端不接触明文与密钥，不保证机密性
 *
 * @author Lys
 * @date 2026/04/28
 */
@Tag(name = "密码库")
@RestController
@AllArgsConstructor
@RequestMapping("/password")
public class PasswordVaultController {

    private final IPasswordVaultService passwordVaultService;

    /**
     * 查询密码列表
     */
    @Operation(summary = "查询密码库记录")
    @GetMapping("/list")
    public ApiResponse<List<PasswordVaultVO>> list(@CurrentUserId String userId) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toPasswordVaultVOList(
                passwordVaultService.listForUser(IdCodec.decode(userId))));
    }

    /**
     * 获取单条密码详情
     */
    @Operation(summary = "查询密码库记录详情")
    @GetMapping("/{id}")
    public ApiResponse<PasswordVaultVO> getById(@CurrentUserId String userId,
                                                @PathVariable String id) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toPasswordVaultVO(
                passwordVaultService.getOwned(IdCodec.decode(userId), IdCodec.decode(id))));
    }

    /**
     * 新增密码
     */
    @Operation(summary = "新增密码库记录")
    @PostMapping
    public ApiResponse<Boolean> save(@CurrentUserId String userId,
                                     @Valid @RequestBody PasswordVaultCreateReq entityReq) {
        PasswordVaultEntity entity = RecordApiConvertor.INSTANCE.fromPasswordVaultCreateReq(entityReq);
        passwordVaultService.createForUser(IdCodec.decode(userId), entity);
        return ApiResponse.success(true);
    }

    /**
     * 编辑密码
     */
    @Operation(summary = "更新密码库记录")
    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(@CurrentUserId String userId,
                                       @PathVariable String id,
                                       @Valid @RequestBody PasswordVaultUpdateReq entityReq) {
        PasswordVaultEntity entity = RecordApiConvertor.INSTANCE.fromPasswordVaultUpdateReq(entityReq);
        passwordVaultService.updateForUser(IdCodec.decode(userId), IdCodec.decode(id), entity);
        return ApiResponse.success(true);
    }

    /**
     * 删除密码
     */
    @Operation(summary = "删除密码库记录")
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@CurrentUserId String userId,
                                       @PathVariable String id) {
        passwordVaultService.deleteForUser(IdCodec.decode(userId), IdCodec.decode(id));
        return ApiResponse.success(true);
    }

    /**
     * 获取分类列表
     */
    @Operation(summary = "查询密码库分类")
    @GetMapping("/categories")
    public ApiResponse<List<String>> categories(@CurrentUserId String userId) {
        return ApiResponse.success(passwordVaultService.listCategories(IdCodec.decode(userId)));
    }
}
