package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.vo.HonorCategoryVO;
import top.aiolife.record.service.IHonorCategoryService;

/**
 * 荣誉分类控制器
 *
 * @author Lys
 * @date 2026/04/11
 */
@Tag(name = "荣誉分类")
@RestController
@AllArgsConstructor
@RequestMapping("/honorCategories")
public class HonorCategoryController {
    private final IHonorCategoryService honorCategoryService;

    @Operation(summary = "查询荣誉分类")
    @GetMapping
    public ApiResponse<List<HonorCategoryVO>> queryCategories(@CurrentUserId String userId) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toHonorCategoryVOList(
                honorCategoryService.listForUser(IdCodec.decode(userId))));
    }
}
