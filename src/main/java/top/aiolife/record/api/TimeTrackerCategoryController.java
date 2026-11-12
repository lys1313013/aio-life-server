package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity;
import top.aiolife.record.pojo.req.TimeTrackerCategoryAdminCreateReq;
import top.aiolife.record.pojo.req.TimeTrackerCategoryAdminUpdateReq;
import top.aiolife.record.pojo.req.TimeTrackerCategoryCreateReq;
import top.aiolife.record.pojo.req.TimeTrackerCategorySortReq;
import top.aiolife.record.pojo.req.TimeTrackerCategoryUpdateReq;
import top.aiolife.record.pojo.vo.TimeTrackerCategoryVO;
import top.aiolife.record.service.ITimeTrackerCategoryService;

/**
 * 时间追踪-分类配置(TimeTrackerCategory) 控制器
 *
 * @author Lys1313013
 * @since 2026-03-07
 */
@Tag(name = "时迹分类 时间分类")
@RestController
@AllArgsConstructor
@RequestMapping("/timeTrackerCategory")
public class TimeTrackerCategoryController {

    private final ITimeTrackerCategoryService categoryService;

    /**
     * 获取当前用户的所有分类（含合并的公共分类）
     */
    @Operation(summary = "查询当前可见的时迹分类")
    @GetMapping("/list")
    public ApiResponse<List<TimeTrackerCategoryVO>> list() {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toTimeTrackerCategoryVOList(categoryService.listUserVisibleCategories(userId)));
    }

    /**
     * 查询合并公共配置后的时迹分类，包含已停用项。
     */
    @Operation(summary = "查询我的时迹分类配置")
    @GetMapping("/all")
    public ApiResponse<List<TimeTrackerCategoryVO>> all() {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toTimeTrackerCategoryVOList(categoryService.listUserCategories(StpUtil.getLoginIdAsLong())));
    }

    @Operation(summary = "查询已隐藏的时迹分类")
    @GetMapping("/hidden")
    public ApiResponse<List<TimeTrackerCategoryVO>> listHidden() {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toTimeTrackerCategoryVOList(categoryService.listUserHiddenCategories(userId)));
    }

    /**
     * 新增分类
     */
    @Operation(summary = "新增时迹分类")
    @PostMapping
    public ApiResponse<Boolean> save(@Valid @RequestBody TimeTrackerCategoryCreateReq entityReq) {
        TimeTrackerCategoryEntity entity = RecordApiConvertor.INSTANCE.fromTimeTrackerCategoryCreateReq(entityReq);
        long userId = StpUtil.getLoginIdAsLong();
        entity.fillCreateCommonField(userId);
        categoryService.createCategory(entity, userId);
        return ApiResponse.success(true);
    }

    /**
     * 更新分类
     */
    @Operation(summary = "更新时迹分类")
    @PutMapping
    public ApiResponse<Boolean> update(@Valid @RequestBody TimeTrackerCategoryUpdateReq entityReq) {
        TimeTrackerCategoryEntity entity = RecordApiConvertor.INSTANCE.fromTimeTrackerCategoryUpdateReq(entityReq);
        long userId = StpUtil.getLoginIdAsLong();
        entity.fillUpdateCommonField(userId);
        categoryService.updateCategory(entity.getId(), entity, userId);
        return ApiResponse.success(true);
    }

    /**
     * 删除分类
     */
    @Operation(summary = "删除或隐藏时迹分类")
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
    @Operation(summary = "调整时迹分类排序")
    @PostMapping("/reSort")
    public ApiResponse<Void> reSort(@Valid @RequestBody List<TimeTrackerCategorySortReq> requests) {
        List<TimeTrackerCategoryEntity> list = requests.stream().map(RecordApiConvertor.INSTANCE::fromTimeTrackerCategorySortReq).toList();
        long userId = StpUtil.getLoginIdAsLong();
        for (TimeTrackerCategoryEntity entity : list) {
            TimeTrackerCategoryEntity update = new TimeTrackerCategoryEntity();
            update.setSortOrder(entity.getSortOrder());

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
    @Operation(summary = "管理员查询全部时迹分类")
    @GetMapping("/admin/list")
    public ApiResponse<List<TimeTrackerCategoryVO>> adminList() {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toTimeTrackerCategoryVOList(categoryService.listAllCategories()));
    }

    @SaCheckRole("admin")
    @Operation(summary = "管理员新增公共时迹分类")
    @PostMapping("/admin")
    public ApiResponse<Boolean> adminSave(@Valid @RequestBody TimeTrackerCategoryAdminCreateReq entityReq) {
        TimeTrackerCategoryEntity entity = RecordApiConvertor.INSTANCE.fromTimeTrackerCategoryAdminCreateReq(entityReq);
        entity.fillCreateCommonField(0L);
        categoryService.adminCreateCategory(entity);
        return ApiResponse.success(true);
    }

    @SaCheckRole("admin")
    @Operation(summary = "管理员更新公共时迹分类")
    @PutMapping("/admin/{id}")
    public ApiResponse<Boolean> adminUpdate(@PathVariable Long id, @Valid @RequestBody TimeTrackerCategoryAdminUpdateReq entityReq) {
        TimeTrackerCategoryEntity entity = RecordApiConvertor.INSTANCE.fromTimeTrackerCategoryAdminUpdateReq(entityReq);
        entity.fillUpdateCommonField(0L);
        categoryService.adminUpdateCategory(id, entity);
        return ApiResponse.success(true);
    }

    @SaCheckRole("admin")
    @Operation(summary = "管理员删除公共时迹分类")
    @DeleteMapping("/admin/{id}")
    public ApiResponse<Boolean> adminDelete(@PathVariable Long id) {
        categoryService.adminDeleteCategory(id);
        return ApiResponse.success(true);
    }
}
