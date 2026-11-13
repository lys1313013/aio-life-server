package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity;
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
    public ApiResponse<List<TimeTrackerCategoryVO>> list(@CurrentUserId String userId) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toTimeTrackerCategoryVOList(
                categoryService.listUserVisibleCategories(IdCodec.decode(userId))));
    }

    /**
     * 查询合并公共配置后的时迹分类，包含已停用项。
     */
    @Operation(summary = "查询我的时迹分类配置")
    @GetMapping("/all")
    public ApiResponse<List<TimeTrackerCategoryVO>> all(@CurrentUserId String userId) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toTimeTrackerCategoryVOList(
                categoryService.listUserCategories(IdCodec.decode(userId))));
    }

    @Operation(summary = "查询已隐藏的时迹分类")
    @GetMapping("/hidden")
    public ApiResponse<List<TimeTrackerCategoryVO>> listHidden(@CurrentUserId String userId) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toTimeTrackerCategoryVOList(
                categoryService.listUserHiddenCategories(IdCodec.decode(userId))));
    }

    /**
     * 新增分类
     */
    @Operation(summary = "新增时迹分类")
    @PostMapping
    public ApiResponse<Boolean> save(@CurrentUserId String userId,
                                     @Valid @RequestBody TimeTrackerCategoryCreateReq entityReq) {
        TimeTrackerCategoryEntity entity = RecordApiConvertor.INSTANCE.fromTimeTrackerCategoryCreateReq(entityReq);
        categoryService.createCategory(entity, IdCodec.decode(userId));
        return ApiResponse.success(true);
    }

    /**
     * 更新分类
     */
    @Operation(summary = "更新时迹分类")
    @PutMapping
    public ApiResponse<Boolean> update(@CurrentUserId String userId,
                                       @Valid @RequestBody TimeTrackerCategoryUpdateReq entityReq) {
        TimeTrackerCategoryEntity entity = RecordApiConvertor.INSTANCE.fromTimeTrackerCategoryUpdateReq(entityReq);
        categoryService.updateCategory(entity.getId(), entity, IdCodec.decode(userId));
        return ApiResponse.success(true);
    }

    /**
     * 删除分类
     */
    @Operation(summary = "删除或隐藏时迹分类")
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@CurrentUserId String userId, @PathVariable String id) {
        categoryService.deleteCategory(IdCodec.decode(id), IdCodec.decode(userId));
        return ApiResponse.success(true);
    }

    /**
     * 拖拽排序
     *
     * @param list 传id/templateId和sort
     */
    @Operation(summary = "调整时迹分类排序")
    @PostMapping("/reSort")
    public ApiResponse<Void> reSort(@CurrentUserId String userId,
                                    @Valid @RequestBody List<TimeTrackerCategorySortReq> requests) {
        List<TimeTrackerCategoryEntity> list = requests.stream()
                .map(RecordApiConvertor.INSTANCE::fromTimeTrackerCategorySortReq).toList();
        categoryService.reSortCategories(list, IdCodec.decode(userId));
        return ApiResponse.success();
    }
}
