package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
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
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.mapper.IPerformanceMapper;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.pojo.entity.PerformanceEntity;
import top.aiolife.record.pojo.req.PerformanceCreateReq;
import top.aiolife.record.pojo.req.PerformanceUpdateReq;
import top.aiolife.record.pojo.vo.PerformanceVO;
import top.aiolife.record.service.IFileService;

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
    public ApiResponse<PageResp<PerformanceVO>> queryPerformances(
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
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toPerformanceVOPage(objectPageResp));
    }

    /**
     * 新增演出记录
     */
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<PerformanceVO> createPerformance(@Valid @RequestBody PerformanceCreateReq entityReq) {
        PerformanceEntity entity = RecordApiConvertor.INSTANCE.fromPerformanceCreateReq(entityReq);
        long userId = StpUtil.getLoginIdAsLong();
        entity.setId(null);
        entity.fillCreateCommonField(userId);
        getBaseMapper().insert(entity);
        syncFiles(entity, userId);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toPerformanceVO(entity));
    }

    /**
     * 更新演出记录
     */
    @PutMapping
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<PerformanceVO> updatePerformance(@Valid @RequestBody PerformanceUpdateReq entityReq) {
        PerformanceEntity entity = RecordApiConvertor.INSTANCE.fromPerformanceUpdateReq(entityReq);
        long userId = StpUtil.getLoginIdAsLong();
        entity.setCreateUser(userId);
        entity.setCreateTime(null);
        entity.fillUpdateCommonField(userId);
        LambdaQueryWrapper<PerformanceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PerformanceEntity::getId, entity.getId());
        wrapper.eq(PerformanceEntity::getCreateUser, userId);
        if (getBaseMapper().update(entity, wrapper) == 0) {
            throw new IllegalArgumentException("演出记录不存在或无权操作");
        }
        syncFiles(entity, userId);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toPerformanceVO(entity));
    }

    // 完整列表语义：未传保留，空列表解除关联；文件实体本身不删除。
    private void syncFiles(PerformanceEntity entity, long userId) {
        if (entity.getFileIds() == null) return;
        List<String> fileIds = entity.getFileIds().stream().distinct().toList();
        if (!fileIds.isEmpty()) {
            List<FileEntity> ownedFiles = fileService.list(new LambdaQueryWrapper<FileEntity>()
                    .in(FileEntity::getId, fileIds).eq(FileEntity::getCreateUser, userId));
            if (ownedFiles.size() != fileIds.size())
                throw new IllegalArgumentException("附件不存在或无权操作");
        }
        LambdaUpdateWrapper<FileEntity> removedFiles = new LambdaUpdateWrapper<>();
        removedFiles.eq(FileEntity::getBizType, "performance")
                .eq(FileEntity::getBizId, entity.getId())
                .eq(FileEntity::getCreateUser, userId)
                .notIn(!fileIds.isEmpty(), FileEntity::getId, fileIds)
                .set(FileEntity::getBizId, null)
                .set(FileEntity::getUpdateUser, userId)
                .set(FileEntity::getUpdateTime, LocalDateTime.now());
        fileService.update(removedFiles);
        fileService.bindBizId(fileIds, "performance", entity.getId());
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
