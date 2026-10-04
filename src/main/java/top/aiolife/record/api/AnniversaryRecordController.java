package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.AnniversaryRecordEntity;
import top.aiolife.record.pojo.req.AnniversaryRecordCreateReq;
import top.aiolife.record.pojo.req.AnniversaryRecordUpdateReq;
import top.aiolife.record.pojo.req.CommonReq;
import top.aiolife.record.pojo.req.HomePinReq;
import top.aiolife.record.pojo.req.HomePinnedOrderReq;
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

    private final IAnniversaryRecordService anniversaryRecordService;

    @GetMapping
    public ApiResponse<List<AnniversaryRecordVO>> queryAnniversaryRecords(@RequestParam(required = false) Integer isPinned) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<AnniversaryRecordEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AnniversaryRecordEntity::getUserId, userId);
        if (isPinned != null) {
            if (isPinned != 0 && isPinned != 1) throw new IllegalArgumentException("固定状态仅支持 0 或 1");
            queryWrapper.eq(AnniversaryRecordEntity::getIsPinned, isPinned);
        }
        if (Integer.valueOf(1).equals(isPinned)) {
            queryWrapper.orderByAsc(AnniversaryRecordEntity::getPinnedSort).orderByDesc(AnniversaryRecordEntity::getId);
        } else {
            queryWrapper.orderByDesc(AnniversaryRecordEntity::getCreateTime).orderByDesc(AnniversaryRecordEntity::getId);
        }

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
        entity = anniversaryRecordService.createForUser(userId, entity);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toAnniversaryRecordVO(entity));
    }

    @PutMapping
    public ApiResponse<AnniversaryRecordVO> updateAnniversaryRecord(@Valid @RequestBody AnniversaryRecordUpdateReq entityReq) {
        AnniversaryRecordEntity entity = RecordApiConvertor.INSTANCE.fromAnniversaryRecordUpdateReq(entityReq);
        long userId = StpUtil.getLoginIdAsLong();
        entity = anniversaryRecordService.updateForUser(userId, entity);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toAnniversaryRecordVO(entity));
    }

    @PostMapping("/batchDelete")
    public ApiResponse<Void> deleteAnniversaryRecords(@RequestBody CommonReq commonReq) {
        long userId = StpUtil.getLoginIdAsLong();
        anniversaryRecordService.deleteForUser(userId, commonReq.getIdList());
        return ApiResponse.success();
    }

    @PutMapping("/{id}/pin")
    public ApiResponse<AnniversaryRecordVO> setPinned(@PathVariable Long id, @Valid @RequestBody HomePinReq request) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toAnniversaryRecordVO(
                anniversaryRecordService.setPinned(StpUtil.getLoginIdAsLong(), id, request.getIsPinned())));
    }

    @PutMapping("/pinned-order")
    public ApiResponse<Void> reorderPinned(@Valid @RequestBody HomePinnedOrderReq request) {
        anniversaryRecordService.reorderPinned(StpUtil.getLoginIdAsLong(), request.getIds());
        return ApiResponse.success();
    }
}
