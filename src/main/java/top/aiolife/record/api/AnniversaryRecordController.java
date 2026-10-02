package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.mapper.IAnniversaryRecordMapper;
import top.aiolife.record.pojo.entity.AnniversaryRecordEntity;
import top.aiolife.record.pojo.req.AnniversaryRecordCreateReq;
import top.aiolife.record.pojo.req.AnniversaryRecordUpdateReq;
import top.aiolife.record.pojo.vo.AnniversaryRecordVO;
import top.aiolife.record.service.IAnniversaryRecordService;

/**
 * 纪念日记录控制器
 *
 * @author Lys
 * @date 2026/04/18
 */
@RestController
@AllArgsConstructor
@RequestMapping("/anniversaryRecords")
public class AnniversaryRecordController {

    private final IAnniversaryRecordMapper anniversaryRecordMapper;
    private final IAnniversaryRecordService anniversaryRecordService;

    @GetMapping
    public ApiResponse<List<AnniversaryRecordVO>> queryAnniversaryRecords() {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<AnniversaryRecordEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AnniversaryRecordEntity::getUserId, userId);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toAnniversaryRecordVOList(anniversaryRecordService.list(queryWrapper)));
    }

    @GetMapping("/{id}")
    public ApiResponse<AnniversaryRecordVO> getAnniversaryRecord(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<AnniversaryRecordEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AnniversaryRecordEntity::getId, id);
        queryWrapper.eq(AnniversaryRecordEntity::getUserId, userId);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toAnniversaryRecordVO(anniversaryRecordService.getOne(queryWrapper)));
    }

    @PostMapping
    public ApiResponse<AnniversaryRecordVO> createAnniversaryRecord(@Valid @RequestBody AnniversaryRecordCreateReq entityReq) {
        AnniversaryRecordEntity entity = RecordApiConvertor.INSTANCE.fromAnniversaryRecordCreateReq(entityReq);
        long userId = StpUtil.getLoginIdAsLong();
        entity.setUserId(userId);
        entity.fillCreateCommonField(userId);
        anniversaryRecordMapper.insert(entity);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toAnniversaryRecordVO(entity));
    }

    @PutMapping
    public ApiResponse<AnniversaryRecordVO> updateAnniversaryRecord(@Valid @RequestBody AnniversaryRecordUpdateReq entityReq) {
        AnniversaryRecordEntity entity = RecordApiConvertor.INSTANCE.fromAnniversaryRecordUpdateReq(entityReq);
        long userId = StpUtil.getLoginIdAsLong();
        entity.fillUpdateCommonField(userId);
        entity.setUserId(null);
        LambdaUpdateWrapper<AnniversaryRecordEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(AnniversaryRecordEntity::getId, entity.getId());
        updateWrapper.eq(AnniversaryRecordEntity::getUserId, userId);
        anniversaryRecordService.update(entity, updateWrapper);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toAnniversaryRecordVO(entity));
    }

    @PostMapping("/batchDelete")
    public ApiResponse<Void> deleteAnniversaryRecords(@RequestBody top.aiolife.record.pojo.req.CommonReq commonReq) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaUpdateWrapper<AnniversaryRecordEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(AnniversaryRecordEntity::getUserId, userId);
        updateWrapper.in(AnniversaryRecordEntity::getId, commonReq.getIdList());
        updateWrapper.set(AnniversaryRecordEntity::getIsDeleted, 1);
        updateWrapper.set(AnniversaryRecordEntity::getUpdateUser, userId);
        updateWrapper.set(AnniversaryRecordEntity::getUpdateTime, java.time.LocalDateTime.now());
        anniversaryRecordService.update(null, updateWrapper);
        return ApiResponse.success();
    }
}
