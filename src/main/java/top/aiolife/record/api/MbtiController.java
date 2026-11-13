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
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.req.MbtiResultSaveReq;
import top.aiolife.record.pojo.vo.MbtiResultDetailVO;
import top.aiolife.record.pojo.vo.MbtiResultVO;
import top.aiolife.record.service.IMbtiResultService;

/**
 * MBTI测试控制器
 *
 * @author Lys
 * @date 2026-03-23
 */
@Tag(name = "MBTI 测试")
@RestController
@RequestMapping("/mbti")
@RequiredArgsConstructor
public class MbtiController {

    private final IMbtiResultService mbtiResultService;

    @Operation(summary = "创建 MBTI 测试")
    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> createTest() {
        return ApiResponse.success(mbtiResultService.createTest());
    }

    @Operation(summary = "查询 MBTI 测试进度及结果")
    @GetMapping("/test/{testId}")
    public ApiResponse<Map<String, Object>> checkTest(@PathVariable String testId) {
        return ApiResponse.success(mbtiResultService.checkTest(testId));
    }

    @Operation(summary = "保存 MBTI 测试结果")
    @PostMapping("/result")
    public ApiResponse<Void> saveResult(@CurrentUserId String userId,
                                        @Valid @RequestBody MbtiResultSaveReq body) {
        mbtiResultService.saveResult(IdCodec.decode(userId), body);
        return ApiResponse.success();
    }

    @Operation(summary = "查询 MBTI 测试历史")
    @GetMapping("/results")
    public ApiResponse<List<MbtiResultVO>> getHistory(@CurrentUserId String userId) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toMbtiResultVOList(
                mbtiResultService.getUserHistory(IdCodec.decode(userId))));
    }

    @Operation(summary = "查询 MBTI 测试结果详情")
    @GetMapping("/result/{id}")
    public ApiResponse<MbtiResultDetailVO> getById(@CurrentUserId String userId, @PathVariable String id) {
        return ApiResponse.success(mbtiResultService.getDetailForUser(
                IdCodec.decode(userId), IdCodec.decode(id)));
    }

    @Operation(summary = "删除 MBTI 测试结果")
    @DeleteMapping("/result/{id}")
    public ApiResponse<Void> deleteResult(@CurrentUserId String userId, @PathVariable String id) {
        mbtiResultService.deleteResultForUser(IdCodec.decode(userId), IdCodec.decode(id));
        return ApiResponse.success();
    }
}
