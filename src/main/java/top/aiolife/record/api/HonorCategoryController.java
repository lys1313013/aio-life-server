package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.HonorCategoryEntity;
import top.aiolife.record.pojo.vo.HonorCategoryVO;
import top.aiolife.record.service.IHonorCategoryService;

/**
 * 荣誉分类控制器
 *
 * @author Lys
 * @date 2026/04/11
 */
@RestController
@AllArgsConstructor
@RequestMapping("/honorCategories")
public class HonorCategoryController {
    private final IHonorCategoryService honorCategoryService;


    @GetMapping
    public ApiResponse<List<HonorCategoryVO>> queryCategories() {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<HonorCategoryEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper
                .eq(HonorCategoryEntity::getUserId, userId)
                .or()
                .isNull(HonorCategoryEntity::getUserId)
        );
        queryWrapper.orderByAsc(HonorCategoryEntity::getSortOrder);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toHonorCategoryVOList(honorCategoryService.list(queryWrapper)));
    }
}
