package top.aiolife.record.api;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity;
import top.aiolife.record.service.ITimeTrackerCategoryService;

import java.util.List;

/**
 * 时间追踪-分类配置(TimeTrackerCategory) 控制器
 *
 * @author Lys1313013
 * @since 2026-03-07
 */
@RestController
@AllArgsConstructor
@RequestMapping("/timeTrackerCategory")
public class TimeTrackerCategoryController {

    private final ITimeTrackerCategoryService categoryService;

    /**
     * 获取当前用户的所有分类（含合并的公共分类）
     */
    @GetMapping("/list")
    public ApiResponse<List<TimeTrackerCategoryEntity>> list() {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(categoryService.listUserVisibleCategories(userId));
    }

    /**
     * 获取当前用户隐藏的分类列表
     */
    @GetMapping("/hidden")
    public ApiResponse<List<TimeTrackerCategoryEntity>> listHidden() {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(categoryService.listUserHiddenCategories(userId));
    }

    /**
     * 新增分类
     */
    @PostMapping
    public ApiResponse<Boolean> save(@RequestBody TimeTrackerCategoryEntity entity) {
        long userId = StpUtil.getLoginIdAsLong();
        entity.fillCreateCommonField(userId);
        categoryService.createCategory(entity, userId);
        return ApiResponse.success(true);
    }

    /**
     * 更新分类
     */
    @PutMapping
    public ApiResponse<Boolean> update(@RequestBody TimeTrackerCategoryEntity entity) {
        long userId = StpUtil.getLoginIdAsLong();
        entity.fillUpdateCommonField(userId);
        categoryService.updateCategory(entity.getId(), entity, userId);
        return ApiResponse.success(true);
    }

    /**
     * 删除分类
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        categoryService.deleteCategory(id, userId);
        return ApiResponse.success(true);
    }

    /**
     * 拖拽排序
     *
     * @param list 传id/templateId和sort
     */
    @PostMapping("/reSort")
    public ApiResponse<Void> reSort(@RequestBody List<TimeTrackerCategoryEntity> list) {
        long userId = StpUtil.getLoginIdAsLong();
        for (TimeTrackerCategoryEntity entity : list) {
            TimeTrackerCategoryEntity update = new TimeTrackerCategoryEntity();
            update.setSort(entity.getSort());
            
            if (entity.getTemplateId() != null) {
                // 如果是公共分类（且可能没有覆盖记录），调用 updateCategory 创建/更新覆盖记录
                categoryService.updateCategory(entity.getTemplateId(), update, userId);
            } else {
                // 私有分类或原记录
                categoryService.updateCategory(entity.getId(), update, userId);
            }
        }
        return ApiResponse.success();
    }

    // ================= 管理员 API =================

    @SaCheckRole("admin")
    @GetMapping("/admin/list")
    public ApiResponse<List<TimeTrackerCategoryEntity>> adminList() {
        return ApiResponse.success(categoryService.listAllCategories());
    }

    @SaCheckRole("admin")
    @PostMapping("/admin")
    public ApiResponse<Boolean> adminSave(@RequestBody TimeTrackerCategoryEntity entity) {
        entity.fillCreateCommonField(0L);
        categoryService.adminCreateCategory(entity);
        return ApiResponse.success(true);
    }

    @SaCheckRole("admin")
    @PutMapping("/admin/{id}")
    public ApiResponse<Boolean> adminUpdate(@PathVariable Long id, @RequestBody TimeTrackerCategoryEntity entity) {
        entity.fillUpdateCommonField(0L);
        categoryService.adminUpdateCategory(id, entity);
        return ApiResponse.success(true);
    }

    @SaCheckRole("admin")
    @DeleteMapping("/admin/{id}")
    public ApiResponse<Boolean> adminDelete(@PathVariable Long id) {
        categoryService.adminDeleteCategory(id);
        return ApiResponse.success(true);
    }
}
