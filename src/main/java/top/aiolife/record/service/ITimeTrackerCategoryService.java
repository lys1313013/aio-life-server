package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity;

import java.util.List;

/**
 * 时间追踪-分类配置表(TimeTrackerCategory) Service 接口
 *
 * @author Lys1313013
 * @since 2026-03-07
 */
public interface ITimeTrackerCategoryService extends IService<TimeTrackerCategoryEntity> {

    /**
     * 查询用户可见的分类列表（应用覆盖和隐藏逻辑）
     * @param userId 用户ID
     * @return 分类列表
     */
    List<TimeTrackerCategoryEntity> listUserVisibleCategories(Long userId);

    /**
     * 查询用户隐藏的分类列表
     * @param userId 用户ID
     * @return 隐藏的分类列表
     */
    List<TimeTrackerCategoryEntity> listUserHiddenCategories(Long userId);

    /**
     * 用户新增分类
     * @param category 分类信息
     * @param userId 用户ID
     */
    void createCategory(TimeTrackerCategoryEntity category, Long userId);

    /**
     * 用户更新分类
     * @param categoryId 分类ID
     * @param updates 更新信息
     * @param userId 用户ID
     */
    void updateCategory(Long categoryId, TimeTrackerCategoryEntity updates, Long userId);

    /**
     * 用户删除/隐藏分类
     * @param categoryId 分类ID
     * @param userId 用户ID
     */
    void deleteCategory(Long categoryId, Long userId);

    /**
     * 获取所有分类（管理员）
     * @return 分类列表
     */
    List<TimeTrackerCategoryEntity> listAllCategories();

    /**
     * 管理员新增公共分类
     * @param category 分类信息
     */
    void adminCreateCategory(TimeTrackerCategoryEntity category);

    /**
     * 管理员更新公共分类
     * @param categoryId 分类ID
     * @param updates 更新信息
     */
    void adminUpdateCategory(Long categoryId, TimeTrackerCategoryEntity updates);

    /**
     * 管理员物理删除公共分类
     * @param categoryId 分类ID
     */
    void adminDeleteCategory(Long categoryId);
}
