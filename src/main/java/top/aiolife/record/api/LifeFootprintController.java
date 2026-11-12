package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;

import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.pojo.req.LifeFootprintSaveReq;
import top.aiolife.record.pojo.req.LifeFootprintBatchSaveReq;
import top.aiolife.record.pojo.vo.LifeFootprintVO;
import top.aiolife.record.service.LifeFootprintService;

@RestController
@Validated
@RequestMapping("/life-footprints")
@RequiredArgsConstructor
@Tag(name = "生活足迹")
public class LifeFootprintController {
    private final LifeFootprintService service;

    @Operation(summary = "查询生活足迹")
    @GetMapping
    public ApiResponse<List<LifeFootprintVO>> list() {
        return ApiResponse.success(service.list(StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "查询生活足迹详情")
    @GetMapping("/{id}")
    public ApiResponse<LifeFootprintVO> get(@PathVariable @Min(1) Long id) {
        return ApiResponse.success(service.get(StpUtil.getLoginIdAsLong(), id));
    }

    @Operation(summary = "新增生活足迹")
    @PostMapping
    public ApiResponse<LifeFootprintVO> create(@Valid @RequestBody LifeFootprintSaveReq request) {
        return ApiResponse.success(service.create(StpUtil.getLoginIdAsLong(), request));
    }

    @Operation(summary = "批量新增生活足迹")
    @PostMapping("/batch")
    public ApiResponse<List<LifeFootprintVO>> saveBatch(@Valid @RequestBody LifeFootprintBatchSaveReq request) {
        return ApiResponse.success(service.saveBatch(StpUtil.getLoginIdAsLong(), request));
    }

    @Operation(summary = "更新生活足迹")
    @PutMapping("/{id}")
    public ApiResponse<LifeFootprintVO> update(@PathVariable @Min(1) Long id, @Valid @RequestBody LifeFootprintSaveReq request) {
        return ApiResponse.success(service.update(StpUtil.getLoginIdAsLong(), id, request));
    }

    @Operation(summary = "删除生活足迹")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Min(1) Long id) {
        service.delete(StpUtil.getLoginIdAsLong(), id);
        return ApiResponse.success();
    }
}
