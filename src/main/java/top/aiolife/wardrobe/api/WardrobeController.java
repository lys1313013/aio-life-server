package top.aiolife.wardrobe.api;

import cn.dev33.satoken.stp.StpUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
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
 * 衣柜 Controller
 */
@Slf4j
@RestController
@RequestMapping("/wardrobe")
@RequiredArgsConstructor
public class WardrobeController {

    private final IWardrobeItemService wardrobeItemService;
    private final IWardrobeCategoryService wardrobeCategoryService;

    // ==================== 衣物接口 ====================

    /**
     * 查询衣物列表
     */
    @GetMapping("/items")
    public ApiResponse<List<WardrobeItemVO>> listItems(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String season,
            @RequestParam(required = false) String keyword) {
        Long userId = StpUtil.getLoginIdAsLong();
        List<WardrobeItemVO> list = wardrobeItemService.listItems(userId, categoryId, season, keyword);
        return ApiResponse.success(list);
    }

    /**
     * 获取衣物详情
     */
    @GetMapping("/items/{id}")
    public ApiResponse<WardrobeItemVO> getItem(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        WardrobeItemVO item = wardrobeItemService.getItem(id, userId);
        return ApiResponse.success(item);
    }

    /**
     * 保存衣物
     */
    @PostMapping("/items")
    public ApiResponse<Void> saveItem(@RequestBody WardrobeItemSaveReq request) {
        WardrobeItemReq req = WardrobeApiConvertor.INSTANCE.fromWardrobeItemSaveReq(request);
        wardrobeItemService.saveItem(req);
        return ApiResponse.success();
    }

    /**
     * 更新衣物
     */
    @PutMapping("/items/{id}")
    public ApiResponse<Void> updateItem(@PathVariable Long id, @RequestBody WardrobeItemSaveReq request) {
        WardrobeItemReq req = WardrobeApiConvertor.INSTANCE.fromWardrobeItemSaveReq(request);
        req.setId(id);
        wardrobeItemService.updateItem(req);
        return ApiResponse.success();
    }

    /**
     * 删除衣物
     */
    @DeleteMapping("/items/{id}")
    public ApiResponse<Void> deleteItem(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        wardrobeItemService.removeItem(id, userId);
        return ApiResponse.success();
    }

    /**
     * 获取统计数据
     */
    @GetMapping("/stats")
    public ApiResponse<WardrobeStatsVO> getStats() {
        Long userId = StpUtil.getLoginIdAsLong();
        WardrobeStatsVO stats = wardrobeItemService.getStats(userId);
        return ApiResponse.success(stats);
    }

    // ==================== 分类接口 ====================

    /**
     * 获取分类列表
     */
    @GetMapping("/categories")
    public ApiResponse<List<CategoryVO>> listCategories() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<CategoryVO> list = wardrobeCategoryService.listCategories(userId);
        return ApiResponse.success(list);
    }

    /**
     * 保存分类
     */
    @PostMapping("/categories")
    public ApiResponse<Void> saveCategory(@RequestBody WardrobeCategorySaveReq request) {
        CategoryReq req = WardrobeApiConvertor.INSTANCE.fromWardrobeCategorySaveReq(request);
        wardrobeCategoryService.saveCategory(req);
        return ApiResponse.success();
    }

    /**
     * 更新分类
     */
    @PutMapping("/categories/{id}")
    public ApiResponse<Void> updateCategory(@PathVariable Long id, @RequestBody WardrobeCategorySaveReq request) {
        CategoryReq req = WardrobeApiConvertor.INSTANCE.fromWardrobeCategorySaveReq(request);
        req.setId(id);
        wardrobeCategoryService.updateCategory(req);
        return ApiResponse.success();
    }

    /**
     * 删除分类
     */
    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        wardrobeCategoryService.removeCategory(id, userId);
        return ApiResponse.success();
    }
}
