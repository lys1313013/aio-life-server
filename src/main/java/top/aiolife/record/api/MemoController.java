package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.mapper.IMemoMapper;
import top.aiolife.record.pojo.entity.MemoEntity;
import top.aiolife.record.service.IMemoService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 备忘录控制器
 *
 * @author Lys
 * @date 2025/12/07
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/memo")
public class MemoController {

    private final IMemoService memoService;
    private final IMemoMapper memoMapper;

    /**
     * 查询列表
     */
    @PostMapping("/query")
    public ApiResponse<PageResp<MemoEntity>> query(@RequestBody CommonQuery<MemoEntity> query) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<MemoEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(MemoEntity::getUserId, userId);
        
        if (query.getCondition() != null && query.getCondition().getContent() != null) {
            lambdaQueryWrapper.like(MemoEntity::getContent, query.getCondition().getContent());
        }
        
        lambdaQueryWrapper.orderByDesc(MemoEntity::getUpdateTime);
        
        Page<MemoEntity> page = new Page<>(query.getPage(), query.getPageSize());
        
        Page<MemoEntity> resultPage = memoMapper.selectPage(page, lambdaQueryWrapper);
        return ApiResponse.success(PageResp.of(resultPage.getRecords(), resultPage.getTotal()));
    }

    /**
     * 新增
     */
    @PostMapping
    public ApiResponse<Boolean> save(@RequestBody MemoEntity entity) {
        entity.setUserId(StpUtil.getLoginIdAsLong());
        entity.setCreateUser(StpUtil.getLoginIdAsLong());
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        memoMapper.insert(entity);
        return ApiResponse.success(true);
    }

    /**
     * 更新
     */
    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(@PathVariable("id") Long id, @RequestBody MemoEntity entity) {
        Long userId = StpUtil.getLoginIdAsLong();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setUpdateUser(userId);
        
        // 只有在修改内容/标题时才更新时间，如果是单纯点击隐藏内容则不更新时间
        if (entity.getContent() != null || entity.getTitle() != null) {
            entity.setUpdateTime(LocalDateTime.now());
        }
        
        LambdaQueryWrapper<MemoEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MemoEntity::getId, entity.getId());
        wrapper.eq(MemoEntity::getUserId, userId);
        
        memoMapper.update(entity, wrapper);
        return ApiResponse.success(true);
    }

    /**
     * 删除
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable("id") Long id) {
        LambdaQueryWrapper<MemoEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MemoEntity::getId, id);
        wrapper.eq(MemoEntity::getUserId, StpUtil.getLoginIdAsLong());
        memoMapper.delete(wrapper);
        return ApiResponse.success(true);
    }
}
