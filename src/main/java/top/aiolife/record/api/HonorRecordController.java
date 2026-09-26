package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.mapper.IHonorRecordMapper;
import top.aiolife.record.pojo.entity.HonorRecordEntity;
import top.aiolife.record.pojo.req.CommonReq;
import top.aiolife.record.service.IHonorRecordService;

import java.time.LocalDateTime;
import top.aiolife.record.service.IFileService;

import java.util.List;

/**
 * 荣誉记录控制器
 *
 * @author Lys
 * @date 2026/04/11
 */
@RestController
@AllArgsConstructor
@RequestMapping("/honorRecords")
public class HonorRecordController {
    private final IHonorRecordMapper honorRecordMapper;

    private final IHonorRecordService honorRecordService;
    private final IFileService fileService;


    @GetMapping
    public ApiResponse<List<HonorRecordEntity>> queryHonorRecords() {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<HonorRecordEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(HonorRecordEntity::getUserId, userId);
        queryWrapper.orderByDesc(HonorRecordEntity::getIsTop);
        queryWrapper.orderByDesc(HonorRecordEntity::getHonorDate);
        List<HonorRecordEntity> list = honorRecordService.list(queryWrapper);
        if (list != null && !list.isEmpty()) {
            for (HonorRecordEntity entity : list) {
                entity.setFiles(fileService.getByBiz("honor_record", entity.getId()));
            }
        }
        return ApiResponse.success(list);
    }

    @GetMapping("/{id}")
    public ApiResponse<HonorRecordEntity> getHonorRecord(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<HonorRecordEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(HonorRecordEntity::getId, id);
        queryWrapper.eq(HonorRecordEntity::getUserId, userId);
        HonorRecordEntity entity = honorRecordService.getOne(queryWrapper);
        if (entity != null) {
            entity.setFiles(fileService.getByBiz("honor_record", entity.getId()));
        }
        return ApiResponse.success(entity);
    }


    @PostMapping
    public ApiResponse<HonorRecordEntity> createHonorRecord(@RequestBody HonorRecordEntity honorRecordEntity) {
        long userId = StpUtil.getLoginIdAsLong();
        honorRecordEntity.setUserId(userId);
        honorRecordEntity.fillCreateCommonField(userId);
        honorRecordMapper.insert(honorRecordEntity);
        if (honorRecordEntity.getFileIds() != null && !honorRecordEntity.getFileIds().isEmpty()) {
            fileService.bindBizId(honorRecordEntity.getFileIds(), "honor_record", honorRecordEntity.getId());
        }
        return ApiResponse.success(honorRecordEntity);
    }


    @PutMapping
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<HonorRecordEntity> updateHonorRecord(@RequestBody HonorRecordEntity honorRecordEntity) {
        long userId = StpUtil.getLoginIdAsLong();
        honorRecordEntity.fillUpdateCommonField(userId);
        honorRecordEntity.setUserId(null);
        LambdaUpdateWrapper<HonorRecordEntity> lambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        lambdaUpdateWrapper.eq(HonorRecordEntity::getId, honorRecordEntity.getId());
        lambdaUpdateWrapper.eq(HonorRecordEntity::getUserId, userId);
        if (!honorRecordService.update(honorRecordEntity, lambdaUpdateWrapper)) {
            throw new IllegalArgumentException("荣誉记录不存在或无权操作");
        }
        if (honorRecordEntity.getFileIds() != null && !honorRecordEntity.getFileIds().isEmpty()) {
            fileService.bindBizId(honorRecordEntity.getFileIds(), "honor_record", honorRecordEntity.getId());
        }
        return ApiResponse.success(honorRecordEntity);
    }


    @PostMapping("/batchDelete")
    public ApiResponse<Void> deleteHonorRecords(@RequestBody CommonReq commonReq) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaUpdateWrapper<HonorRecordEntity> lambdaQueryWrapper = new LambdaUpdateWrapper<>();
        lambdaQueryWrapper.eq(HonorRecordEntity::getUserId, userId);
        lambdaQueryWrapper.in(HonorRecordEntity::getId, commonReq.getIdList());
        lambdaQueryWrapper.set(HonorRecordEntity::getIsDeleted, 1);
        lambdaQueryWrapper.set(HonorRecordEntity::getUpdateTime, LocalDateTime.now());
        lambdaQueryWrapper.set(HonorRecordEntity::getUpdateUser, userId);
        honorRecordService.update(null, lambdaQueryWrapper);
        return ApiResponse.success();
    }


    @PostMapping("/toggleTop/{id}")
    public ApiResponse<Void> toggleTop(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<HonorRecordEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(HonorRecordEntity::getId, id);
        queryWrapper.eq(HonorRecordEntity::getUserId, userId);
        HonorRecordEntity record = honorRecordService.getOne(queryWrapper);
        
        if (record != null) {
            record.setIsTop(record.getIsTop() == 1 ? 0 : 1);
            record.fillUpdateCommonField(userId);
            LambdaUpdateWrapper<HonorRecordEntity> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(HonorRecordEntity::getId, id);
            updateWrapper.eq(HonorRecordEntity::getUserId, userId);
            honorRecordService.update(record, updateWrapper);
        }
        return ApiResponse.success();
    }
}
