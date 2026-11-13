package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.pojo.req.CbtiTestReq;
import top.aiolife.record.service.ICbtiService;

/**
 * CBTI 测试控制器
 *
 * <p>用途：提供 CBTI 题库、人格列表、结果计算与历史记录接口。</p>
 *
 * @author Ethan
 * @date 2026/04/18
 */
@Tag(name = "CBTI 测试")
@RestController
@RequestMapping("/cbti")
@RequiredArgsConstructor
public class CbtiController {

    private final ICbtiService cbtiService;

    @Operation(summary = "查询 CBTI 题库和维度")
    @GetMapping("/questions")
    public ApiResponse<Map<String, Object>> questions() {
        return ApiResponse.success(cbtiService.getQuestions());
    }

    @Operation(summary = "查询 CBTI 人格类型")
    @GetMapping("/personalities")
    public ApiResponse<List<Map<String, Object>>> personalities() {
        return ApiResponse.success(cbtiService.listPersonalityViews());
    }

    @Operation(summary = "查询 CBTI 人格详情")
    @GetMapping("/personalities/{code}")
    public ApiResponse<Map<String, Object>> personality(@PathVariable String code) {
        return ApiResponse.success(cbtiService.getPersonalityView(code));
    }

    @Operation(summary = "计算并保存 CBTI 测试结果")
    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> test(@CurrentUserId String userId,
                                                 @Valid @RequestBody CbtiTestReq body) {
        Map<Integer, Integer> answers = body.getAnswers();
        return ApiResponse.success(cbtiService.testAndSave(
                IdCodec.decode(userId), answers == null ? Map.of() : answers, body.getHiddenAnswers()));
    }

    @Operation(summary = "查询 CBTI 测试历史")
    @GetMapping("/results")
    public ApiResponse<List<Map<String, Object>>> results(@CurrentUserId String userId) {
        return ApiResponse.success(cbtiService.getUserHistoryView(IdCodec.decode(userId)));
    }

    @Operation(summary = "查询 CBTI 测试结果详情")
    @GetMapping("/result/{id}")
    public ApiResponse<Map<String, Object>> resultDetail(@CurrentUserId String userId, @PathVariable String id) {
        return ApiResponse.success(cbtiService.getHistoryDetailView(
                IdCodec.decode(id), IdCodec.decode(userId)));
    }

    @Operation(summary = "删除 CBTI 测试结果")
    @DeleteMapping("/result/{id}")
    public ApiResponse<Boolean> deleteResult(@CurrentUserId String userId, @PathVariable String id) {
        cbtiService.deleteHistoryForUser(IdCodec.decode(id), IdCodec.decode(userId));
        return ApiResponse.success(true);
    }
}
