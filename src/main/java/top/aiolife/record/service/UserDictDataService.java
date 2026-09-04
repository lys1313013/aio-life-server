package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.pojo.vo.UserDictDataSortVO;

import java.util.List;

/**
 * 用户字典数据服务接口
 *
 * @author Lys
 */
public interface UserDictDataService extends IService<UserDictDataEntity> {

    /**
     * 获取用户可见的字典数据（合并基础值和个人配置）
     */
    List<UserDictDataEntity> listUserVisibleDictData(Long userId, String dictType);

    /**
     * 获取用户可见的字典数据
     *
     * @param includeDisabled 是否包含已停用记录。true 用于管理页（可查/可改停用项），false 用于业务页（仅展示启用项）
     */
    List<UserDictDataEntity> listUserVisibleDictData(Long userId, String dictType, boolean includeDisabled);

    /**
     * 创建字典数据
     */
    void createDictData(UserDictDataEntity entity, Long userId);

    /**
     * 更新字典数据（支持覆盖基础值）
     */
    void updateDictData(Long id, UserDictDataEntity entity, Long userId);

    /**
     * 删除/隐藏字典数据（支持隐藏基础值）
     */
    void deleteDictData(Long id, Long userId);

    /**
     * 重新排列基础字典数据。
     */
    List<UserDictDataSortVO> reSortBaseDictData(String dictType, Long dragId, Long targetId, String position);
}
