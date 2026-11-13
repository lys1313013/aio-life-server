package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
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
import top.aiolife.record.pojo.req.LifeFootprintSaveReq;
import top.aiolife.record.pojo.req.LifeFootprintBatchSaveReq;
import top.aiolife.record.pojo.vo.LifeFootprintVO;
import top.aiolife.record.service.LifeFootprintService;

@Tag(name = "生活足迹")
@RestController
@Validated
@RequestMapping("/life-footprints")
@RequiredArgsConstructor
public class LifeFootprintController {
    private final LifeFootprintService service;

    @Operation(summary = "查询生活足迹")
    @GetMapping
    public ApiResponse<List<LifeFootprintVO>> list(@CurrentUserId String userId) {
        return ApiResponse.success(service.list(IdCodec.decode(userId)));
    }

    @Operation(summary = "查询生活足迹详情")
    @GetMapping("/{id}")
    public ApiResponse<LifeFootprintVO> get(@CurrentUserId String userId,
                                            @PathVariable String id) {
        return ApiResponse.success(service.get(IdCodec.decode(userId), IdCodec.decode(id)));
    }

    @Operation(summary = "新增生活足迹")
    @PostMapping
    public ApiResponse<LifeFootprintVO> create(@CurrentUserId String userId,
                                               @Valid @RequestBody LifeFootprintSaveReq request) {
        return ApiResponse.success(service.create(IdCodec.decode(userId), request));
    }

    @Operation(summary = "批量新增生活足迹")
    @PostMapping("/batch")
    public ApiResponse<List<LifeFootprintVO>> saveBatch(@CurrentUserId String userId,
                                                        @Valid @RequestBody LifeFootprintBatchSaveReq request) {
        return ApiResponse.success(service.saveBatch(IdCodec.decode(userId), request));
    }

    @Operation(summary = "更新生活足迹")
    @PutMapping("/{id}")
    public ApiResponse<LifeFootprintVO> update(@CurrentUserId String userId,
                                               @PathVariable String id,
                                               @Valid @RequestBody LifeFootprintSaveReq request) {
        return ApiResponse.success(service.update(IdCodec.decode(userId), IdCodec.decode(id), request));
    }

    @Operation(summary = "删除生活足迹")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@CurrentUserId String userId,
                                    @PathVariable String id) {
        service.delete(IdCodec.decode(userId), IdCodec.decode(id));
        return ApiResponse.success();
    }
}
