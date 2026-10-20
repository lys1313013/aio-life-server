package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.pojo.req.BodyProfileSaveReq;
import top.aiolife.record.pojo.vo.BodyProfileVO;
import top.aiolife.record.service.BodyProfileService;

@RestController
@RequestMapping("/body-profile")
@RequiredArgsConstructor
@Tag(name = "身体档案")
public class BodyProfileController {
    private final BodyProfileService service;

    @GetMapping
    @Operation(summary = "读取自己的当前身体档案")
    public ApiResponse<BodyProfileVO> get() {
        return ApiResponse.success(service.get(StpUtil.getLoginIdAsLong()));
    }

    @PutMapping
    @Operation(summary = "完整替换自己的当前身体档案", description = "可空字段传 null 清空，不保存历史记录；version 必须与读取结果相同。BMI 自动计算。")
    public ApiResponse<BodyProfileVO> save(@Valid @RequestBody BodyProfileSaveReq request) {
        return ApiResponse.success(service.save(StpUtil.getLoginIdAsLong(), request));
    }
}
