package top.aiolife.record.api;

import cn.dev33.satoken.annotation.SaCheckRole;
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
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity;
import top.aiolife.record.pojo.req.TimeTrackerCategoryAdminCreateReq;
import top.aiolife.record.pojo.req.TimeTrackerCategoryAdminUpdateReq;
import top.aiolife.record.pojo.vo.TimeTrackerCategoryVO;
import top.aiolife.record.service.ITimeTrackerCategoryService;

/**
 * 时间追踪-公共分类管理员控制器
 *
 * @author Lys1313013
 * @since 2026-03-07
 */
@Tag(name = "时迹分类 时间分类（管理员）")
@SaCheckRole("admin")
@RestController
@AllArgsConstructor
@RequestMapping("/timeTrackerCategory")
public class TimeTrackerCategoryAdminController {

    private final ITimeTrackerCategoryService categoryService;

    @Operation(summary = "管理员查询全部时迹分类")
    @GetMapping("/admin/list")
    public ApiResponse<List<TimeTrackerCategoryVO>> adminList() {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toTimeTrackerCategoryVOList(
                categoryService.listAllCategories()));
    }

    @Operation(summary = "管理员新增公共时迹分类")
    @PostMapping("/admin")
    public ApiResponse<Boolean> adminSave(@Valid @RequestBody TimeTrackerCategoryAdminCreateReq entityReq) {
        TimeTrackerCategoryEntity entity = RecordApiConvertor.INSTANCE.fromTimeTrackerCategoryAdminCreateReq(entityReq);
        categoryService.adminCreateCategory(entity);
        return ApiResponse.success(true);
    }

    @Operation(summary = "管理员更新公共时迹分类")
    @PutMapping("/admin/{id}")
    public ApiResponse<Boolean> adminUpdate(@PathVariable String id,
                                            @Valid @RequestBody TimeTrackerCategoryAdminUpdateReq entityReq) {
        TimeTrackerCategoryEntity entity = RecordApiConvertor.INSTANCE.fromTimeTrackerCategoryAdminUpdateReq(entityReq);
        categoryService.adminUpdateCategory(IdCodec.decode(id), entity);
        return ApiResponse.success(true);
    }

    @Operation(summary = "管理员删除公共时迹分类")
    @DeleteMapping("/admin/{id}")
    public ApiResponse<Boolean> adminDelete(@PathVariable String id) {
        categoryService.adminDeleteCategory(IdCodec.decode(id));
        return ApiResponse.success(true);
    }
}
