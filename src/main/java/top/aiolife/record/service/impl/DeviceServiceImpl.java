package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.record.enums.FileBizType;
import top.aiolife.record.service.RecordImageService;
import top.aiolife.core.mybatis.IlikeQuery;
import top.aiolife.core.query.QueryOrder;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.util.SysUtil;
import top.aiolife.record.mapper.IDeviceMapper;
import top.aiolife.record.pojo.entity.DeviceEntity;
import top.aiolife.record.pojo.query.DeviceQuery;
import top.aiolife.record.service.IDeviceService;

/**
 * 设备服务实现
 *
 * @author Lys
 * @date 2025/04/04 19:21
 */
@Service
@RequiredArgsConstructor
public class DeviceServiceImpl extends ServiceImpl<IDeviceMapper, DeviceEntity> implements IDeviceService {

    private final RecordImageService images;

    @Override
    public PageResp<DeviceEntity> pageForUser(long userId, DeviceQuery condition, int page, int pageSize) {
        LambdaQueryWrapper<DeviceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceEntity::getUserId, userId);
        if (condition != null) {
            wrapper.eq(SysUtil.isNotEmpty(condition.getType()), DeviceEntity::getType, condition.getType());
            IlikeQuery.anyOf(wrapper, SysUtil.isNotEmpty(condition.getName()), condition.getName(),
                    DeviceEntity::getName);
        }
        QueryOrder.descNullsLast(wrapper, DeviceEntity::getPurchaseDate).orderByDesc(DeviceEntity::getId);
        Page<DeviceEntity> result = baseMapper.selectPage(new Page<>(page, pageSize), wrapper);
        images.fill(userId, FileBizType.DEVICE, result.getRecords(), DeviceEntity::getId, DeviceEntity::setFileId);
        return PageResp.of(result.getRecords(), result.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createForUser(long userId, DeviceEntity entity) {
        entity.setId(null);
        entity.setUserId(userId);
        entity.fillCreateCommonField(userId);
        if (baseMapper.insert(entity) == 0) return false;
        images.replace(userId, FileBizType.DEVICE, entity.getId(), entity.getFileId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateForUser(long userId, long id, DeviceEntity entity) {
        entity.setId(id);
        entity.setUserId(null);
        entity.fillUpdateCommonField(userId);
        LambdaQueryWrapper<DeviceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceEntity::getId, id);
        wrapper.eq(DeviceEntity::getUserId, userId);
        if (baseMapper.update(entity, wrapper) == 0) return false;
        images.replace(userId, FileBizType.DEVICE, id, entity.getFileId());
        return true;
    }

    @Override
    public DeviceEntity getOwnedOrNull(long userId, long id) {
        DeviceEntity entity = baseMapper.selectOne(new LambdaQueryWrapper<DeviceEntity>()
                .eq(DeviceEntity::getId, id)
                .eq(DeviceEntity::getUserId, userId));
        if (entity != null) entity.setFileId(images.find(userId, FileBizType.DEVICE, id));
        return entity;
    }
}
