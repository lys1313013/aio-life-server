package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.mapper.IPerformanceMapper;
import top.aiolife.record.pojo.entity.PerformanceEntity;
import top.aiolife.record.service.IFileService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演出记录控制器
 *
 * @author Lys
 * @date 2025/04/07 22:31
 */
@RestController
@AllArgsConstructor
@RequestMapping("/performance")
public class PerformanceController {

    private IPerformanceMapper performanceMapper;
    private final IFileService fileService;

    public IPerformanceMapper getBaseMapper() {
        return performanceMapper;
    }

    /**
     * 分页查询演出记录
     */
    @GetMapping
    public ApiResponse<PageResp<PerformanceEntity>> queryPerformances(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        LambdaQueryWrapper<PerformanceEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(PerformanceEntity::getCreateUser, StpUtil.getLoginIdAsLong());

        Page<PerformanceEntity> pageParam = new Page<>(page, pageSize);
        IPage<PerformanceEntity> iPage = getBaseMapper().selectPage(pageParam, lambdaQueryWrapper);
        if (iPage.getRecords() != null) {
            for (PerformanceEntity entity : iPage.getRecords()) {
                entity.setFiles(fileService.getByBiz("performance", entity.getId()));
            }
        }
        PageResp<PerformanceEntity> objectPageResp = PageResp.of(iPage.getRecords(), iPage.getTotal());
        return ApiResponse.success(objectPageResp);
    }

    /**
     * 新增演出记录
     */
    @PostMapping
    public ApiResponse<PerformanceEntity> createPerformance(@RequestBody PerformanceEntity entity) {
        long userId = StpUtil.getLoginIdAsLong();
        entity.setId(null);
        entity.fillCreateCommonField(userId);
        getBaseMapper().insert(entity);
        if (entity.getFileIds() != null && !entity.getFileIds().isEmpty()) {
            fileService.bindBizId(entity.getFileIds(), "performance", entity.getId());
        }
        return ApiResponse.success(entity);
    }

    /**
     * 更新演出记录
     */
    @PutMapping
    public ApiResponse<PerformanceEntity> updatePerformance(@RequestBody PerformanceEntity entity) {
        long userId = StpUtil.getLoginIdAsLong();
        entity.fillUpdateCommonField(userId);
        LambdaQueryWrapper<PerformanceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PerformanceEntity::getId, entity.getId());
        wrapper.eq(PerformanceEntity::getCreateUser, userId);
        getBaseMapper().update(entity, wrapper);
        if (entity.getFileIds() != null && !entity.getFileIds().isEmpty()) {
            fileService.bindBizId(entity.getFileIds(), "performance", entity.getId());
        }
        return ApiResponse.success(entity);
    }

    /**
     * 删除演出记录
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> deletePerformance(@PathVariable Long id) {
        LambdaQueryWrapper<PerformanceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PerformanceEntity::getId, id);
        wrapper.eq(PerformanceEntity::getCreateUser, StpUtil.getLoginIdAsLong());
        boolean b = getBaseMapper().delete(wrapper) > 0;
        return ApiResponse.success(b);
    }

}
