package top.aiolife.wardrobe.service;

import com.baomidou.mybatisplus.extension.service.IService;
import top.aiolife.wardrobe.pojo.entity.WardrobeItemEntity;
import top.aiolife.wardrobe.pojo.req.WardrobeItemReq;
import top.aiolife.wardrobe.pojo.vo.WardrobeItemVO;
import top.aiolife.wardrobe.pojo.vo.WardrobeStatsVO;

import java.util.List;

/**
 * 衣柜衣物 Service
 */
public interface IWardrobeItemService extends IService<WardrobeItemEntity> {

    /**
     * 保存衣物
     */
    void saveItem(Long userId, WardrobeItemReq req);

    /**
     * 更新衣物
     */
    void updateItem(Long userId, WardrobeItemReq req);

    /**
     * 删除衣物
     */
    void removeItem(Long id, Long userId);

    /**
     * 获取衣物详情
     */
    WardrobeItemVO getItem(Long id, Long userId);

    /**
     * 查询衣物列表
     */
    List<WardrobeItemVO> listItems(Long userId, Long categoryId, String season, String keyword);

    /**
     * 获取统计数据
     */
    WardrobeStatsVO getStats(Long userId);
}
