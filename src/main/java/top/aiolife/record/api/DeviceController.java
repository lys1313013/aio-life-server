package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.util.SysUtil;
import top.aiolife.record.mapper.IDeviceMapper;
import top.aiolife.record.pojo.entity.DeviceEntity;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/04/04 19:22
 */
@RestController
@AllArgsConstructor
@RequestMapping("/device")
public class DeviceController {
    private IDeviceMapper eleDeviceMapper;

    public IDeviceMapper getBaseMapper() {
        return eleDeviceMapper;
    }

    @PostMapping("/query")
    public ApiResponse<PageResp<DeviceEntity>> query(
            @RequestBody CommonQuery<DeviceEntity> query) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<DeviceEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(DeviceEntity::getUserId, userId);
        DeviceEntity condition = query.getCondition();
        lambdaQueryWrapper.eq(SysUtil.isNotEmpty(condition.getType()), DeviceEntity::getType,
                condition.getType());
        lambdaQueryWrapper.orderByDesc(DeviceEntity::getPurchaseDate);        // 分页
        Page<DeviceEntity> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<DeviceEntity> iPage = getBaseMapper().selectPage(page, lambdaQueryWrapper);
        PageResp<DeviceEntity> objectPageResp = PageResp.of(iPage.getRecords(), iPage.getTotal());
        return ApiResponse.success(objectPageResp);
    }

    /**
     * 新增
     *
     * @param entity
     */
    @PostMapping
    public ApiResponse<Boolean> add(@RequestBody DeviceEntity entity) {
        long userId = StpUtil.getLoginIdAsLong();
        entity.setId(null);
        entity.setUserId(userId);
        entity.fillCreateCommonField(userId);
        return ApiResponse.success(getBaseMapper().insert(entity) > 0);
    }

    /**
     * 更新
     *
     * @param id
     * @param entity
     */
    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(@PathVariable("id") Long id, @RequestBody DeviceEntity entity) {
        long userId = StpUtil.getLoginIdAsLong();
        entity.setId(id);
        entity.setUserId(null);
        entity.fillUpdateCommonField(userId);
        LambdaQueryWrapper<DeviceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceEntity::getId, id);
        wrapper.eq(DeviceEntity::getUserId, userId);
        return ApiResponse.success(getBaseMapper().update(entity, wrapper) > 0);
    }

    /**
     * 删除
     *
     * @param id
     * @return
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable("id") Long id) {
        LambdaQueryWrapper<DeviceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceEntity::getId, id);
        wrapper.eq(DeviceEntity::getUserId, StpUtil.getLoginIdAsLong());
        return ApiResponse.success(getBaseMapper().delete(wrapper) > 0);
    }

    @GetMapping("/{id}")
    public ApiResponse<DeviceEntity> getDevice(@PathVariable("id") Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<DeviceEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DeviceEntity::getId, id);
        queryWrapper.eq(DeviceEntity::getUserId, userId);
        DeviceEntity entity = getBaseMapper().selectOne(queryWrapper);
        return ApiResponse.success(entity);
    }

}
