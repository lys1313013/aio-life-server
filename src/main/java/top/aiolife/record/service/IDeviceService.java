package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.pojo.entity.DeviceEntity;
import top.aiolife.record.pojo.query.DeviceQuery;

/**
 * 设备服务
 *
 * @author Lys
 * @date 2025/04/04 19:21
 */
public interface IDeviceService extends IService<DeviceEntity> {

    /** 当前用户的设备分页，购买日期倒序、空值在后。 */
    PageResp<DeviceEntity> pageForUser(long userId, DeviceQuery condition, int page, int pageSize);

    /** 新增设备，用户归属和审计字段由服务端设置。 */
    boolean createForUser(long userId, DeviceEntity entity);

    /** 仅更新当前用户拥有的设备，不允许修改归属。 */
    boolean updateForUser(long userId, long id, DeviceEntity entity);

    /** 查询当前用户的设备，不存在时返回 null。 */
    DeviceEntity getOwnedOrNull(long userId, long id);
}
