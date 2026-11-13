package top.aiolife.wardrobe.service;

import com.baomidou.mybatisplus.extension.service.IService;
import top.aiolife.wardrobe.pojo.entity.WardrobeCategoryEntity;
import top.aiolife.wardrobe.pojo.req.CategoryReq;
import top.aiolife.wardrobe.pojo.vo.CategoryVO;

import java.util.List;

/**
 * 衣柜分类 Service
 */
public interface IWardrobeCategoryService extends IService<WardrobeCategoryEntity> {

    /**
     * 保存分类
     */
    void saveCategory(Long userId, CategoryReq req);

    /**
     * 更新分类
     */
    void updateCategory(Long userId, CategoryReq req);

    /**
     * 删除分类
     */
    void removeCategory(Long id, Long userId);

    /**
     * 获取分类列表
     */
    List<CategoryVO> listCategories(Long userId);
}
