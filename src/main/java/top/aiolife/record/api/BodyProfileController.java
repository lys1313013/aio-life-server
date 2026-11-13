package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.pojo.req.BodyProfileSaveReq;
import top.aiolife.record.pojo.vo.BodyProfileVO;
import top.aiolife.record.service.BodyProfileService;

/**
 * 身体数据控制器
 *
 * @author Lys
 */
@RestController
@RequestMapping("/body-profile")
@RequiredArgsConstructor
@Tag(name = "身体数据")
public class BodyProfileController {

    private final BodyProfileService service;

    @GetMapping
    @Operation(summary = "查询当前身体数据")
    public ApiResponse<BodyProfileVO> get(@CurrentUserId String userId) {
        return ApiResponse.success(service.get(IdCodec.decode(userId)));
    }

    @PutMapping
    @Operation(summary = "替换当前身体数据", description = "可空字段传 null 清空，不保存历史记录；version 必须与读取结果相同。BMI 自动计算。")
    public ApiResponse<BodyProfileVO> save(@CurrentUserId String userId,
                                           @Valid @RequestBody BodyProfileSaveReq request) {
        return ApiResponse.success(service.save(IdCodec.decode(userId), request));
    }
}
