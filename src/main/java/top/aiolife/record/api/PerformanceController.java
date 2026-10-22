package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
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
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.query.PaginationRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.PerformanceEntity;
import top.aiolife.record.pojo.req.PerformanceCreateReq;
import top.aiolife.record.pojo.req.PerformanceUpdateReq;
import top.aiolife.record.pojo.vo.PerformanceVO;
import top.aiolife.record.service.PerformanceRecordService;

/**
 * 演出记录控制器
 *
 * @author Lys
 * @date 2025/04/07 22:31
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/performance")
public class PerformanceController {

    private final PerformanceRecordService performanceRecords;

    /**
     * 分页查询演出记录
     */
    @GetMapping
    public ApiResponse<PageResp<PerformanceVO>> queryPerformances(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        try {
            PaginationRequest.validate(page, pageSize, PaginationRequest.MAX_PAGE_SIZE);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
        PageResp<PerformanceEntity> objectPageResp = performanceRecords.list(StpUtil.getLoginIdAsLong(), page, pageSize);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toPerformanceVOPage(objectPageResp));
    }

    /**
     * 新增演出记录
     */
    @PostMapping
    public ApiResponse<PerformanceVO> createPerformance(@Valid @RequestBody PerformanceCreateReq entityReq) {
        PerformanceEntity entity = RecordApiConvertor.INSTANCE.fromPerformanceCreateReq(entityReq);
        long userId = StpUtil.getLoginIdAsLong();
        entity = performanceRecords.create(entity, userId);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toPerformanceVO(entity));
    }

    /**
     * 更新演出记录
     */
    @PutMapping
    public ApiResponse<PerformanceVO> updatePerformance(@Valid @RequestBody PerformanceUpdateReq entityReq) {
        PerformanceEntity entity = RecordApiConvertor.INSTANCE.fromPerformanceUpdateReq(entityReq);
        long userId = StpUtil.getLoginIdAsLong();
        entity = performanceRecords.update(entity, userId);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toPerformanceVO(entity));
    }

    /**
     * 删除演出记录
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> deletePerformance(@PathVariable Long id) {
        boolean b = performanceRecords.delete(id, StpUtil.getLoginIdAsLong());
        return ApiResponse.success(b);
    }

}
