package top.aiolife.wardrobe.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.wardrobe.convertor.WardrobeApiConvertor;
import top.aiolife.wardrobe.pojo.req.CategoryReq;
import top.aiolife.wardrobe.pojo.req.WardrobeCategorySaveReq;
import top.aiolife.wardrobe.pojo.req.WardrobeItemReq;
import top.aiolife.wardrobe.pojo.req.WardrobeItemSaveReq;
import top.aiolife.wardrobe.pojo.vo.CategoryVO;
import top.aiolife.wardrobe.pojo.vo.WardrobeItemVO;
import top.aiolife.wardrobe.pojo.vo.WardrobeStatsVO;
import top.aiolife.wardrobe.service.IWardrobeCategoryService;
import top.aiolife.wardrobe.service.IWardrobeItemService;

/**
 * 衣柜控制器。
 *
 * @author Lys
 * @date 2026/11/13
 */
@Slf4j
@Tag(name = "衣柜")
@RestController
@RequestMapping("/wardrobe")
@RequiredArgsConstructor
public class WardrobeController {

    private final IWardrobeItemService wardrobeItemService;
    private final IWardrobeCategoryService wardrobeCategoryService;

    @Operation(summary = "查询衣物列表")
    @GetMapping("/items")
    public ApiResponse<List<WardrobeItemVO>> listItems(
            @CurrentUserId String userId,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String season,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(wardrobeItemService.listItems(
                IdCodec.decode(userId), IdCodec.decodeOrNull(categoryId), season, keyword));
    }

    @Operation(summary = "查询衣物详情")
    @GetMapping("/items/{id}")
    public ApiResponse<WardrobeItemVO> getItem(@CurrentUserId String userId, @PathVariable String id) {
        return ApiResponse.success(wardrobeItemService.getItem(
                IdCodec.decode(id), IdCodec.decode(userId)));
    }

    @Operation(summary = "新增衣物")
    @PostMapping("/items")
    public ApiResponse<Void> saveItem(@CurrentUserId String userId,
                                      @Valid @RequestBody WardrobeItemSaveReq request) {
        WardrobeItemReq req = WardrobeApiConvertor.INSTANCE.fromWardrobeItemSaveReq(request);
        wardrobeItemService.saveItem(IdCodec.decode(userId), req);
        return ApiResponse.success();
    }

    @Operation(summary = "更新衣物")
    @PutMapping("/items/{id}")
    public ApiResponse<Void> updateItem(@CurrentUserId String userId, @PathVariable String id,
                                        @Valid @RequestBody WardrobeItemSaveReq request) {
        WardrobeItemReq req = WardrobeApiConvertor.INSTANCE.fromWardrobeItemSaveReq(request);
        req.setId(IdCodec.decode(id));
        wardrobeItemService.updateItem(IdCodec.decode(userId), req);
        return ApiResponse.success();
    }

    @Operation(summary = "删除衣物")
    @DeleteMapping("/items/{id}")
    public ApiResponse<Void> deleteItem(@CurrentUserId String userId, @PathVariable String id) {
        wardrobeItemService.removeItem(IdCodec.decode(id), IdCodec.decode(userId));
        return ApiResponse.success();
    }

    @Operation(summary = "查询衣柜统计")
    @GetMapping("/stats")
    public ApiResponse<WardrobeStatsVO> getStats(@CurrentUserId String userId) {
        return ApiResponse.success(wardrobeItemService.getStats(IdCodec.decode(userId)));
    }

    @Operation(summary = "查询衣物分类")
    @GetMapping("/categories")
    public ApiResponse<List<CategoryVO>> listCategories(@CurrentUserId String userId) {
        return ApiResponse.success(wardrobeCategoryService.listCategories(IdCodec.decode(userId)));
    }

    @Operation(summary = "新增衣物分类")
    @PostMapping("/categories")
    public ApiResponse<Void> saveCategory(@CurrentUserId String userId,
                                          @Valid @RequestBody WardrobeCategorySaveReq request) {
        CategoryReq req = WardrobeApiConvertor.INSTANCE.fromWardrobeCategorySaveReq(request);
        wardrobeCategoryService.saveCategory(IdCodec.decode(userId), req);
        return ApiResponse.success();
    }

    @Operation(summary = "更新衣物分类")
    @PutMapping("/categories/{id}")
    public ApiResponse<Void> updateCategory(@CurrentUserId String userId, @PathVariable String id,
                                            @Valid @RequestBody WardrobeCategorySaveReq request) {
        CategoryReq req = WardrobeApiConvertor.INSTANCE.fromWardrobeCategorySaveReq(request);
        req.setId(IdCodec.decode(id));
        wardrobeCategoryService.updateCategory(IdCodec.decode(userId), req);
        return ApiResponse.success();
    }

    @Operation(summary = "删除衣物分类")
    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> deleteCategory(@CurrentUserId String userId, @PathVariable String id) {
        wardrobeCategoryService.removeCategory(IdCodec.decode(id), IdCodec.decode(userId));
        return ApiResponse.success();
    }
}
