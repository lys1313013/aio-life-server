package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.util.SysUtil;

import top.aiolife.record.mapper.UserDictDataMapper;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.service.UserDictDataService;

import java.util.List;

/**
 * 用户字典数据Controller
 *
 * @author Lys
 */
@RestController
@AllArgsConstructor
@RequestMapping("/userDictData")
public class UserDictDataController {

    private UserDictDataMapper userDictDataMapper;
    private UserDictDataService userDictDataService;

    @PostMapping("/query")
    public ApiResponse<PageResp<UserDictDataEntity>> query(@RequestBody CommonQuery<UserDictDataEntity> query) {
        Long userId = StpUtil.getLoginIdAsLong();

        UserDictDataEntity condition = query.getCondition();
        String dictType = (condition != null) ? condition.getDictType() : null;

        // 管控页需要看到所有记录（含已停用），方便用户改回启用或删除
        List<UserDictDataEntity> dataList = userDictDataService.listUserVisibleDictData(userId, dictType, true);

        // 如果有额外的查询条件，在内存中进行过滤
        if (condition != null) {
            dataList = dataList.stream().filter(item -> {
                boolean match = true;
                if (SysUtil.isNotEmpty(condition.getDictLabel())) {
                    match = item.getDictLabel() != null && item.getDictLabel().contains(condition.getDictLabel());
                }
                if (match && SysUtil.isNotEmpty(condition.getStatus())) {
                    match = condition.getStatus().equals(item.getStatus());
                }
                return match;
            }).toList();
        }

        // 内存分页
        int current = query.getPage() != null ? query.getPage() : 1;
        int size = query.getPageSize() != null ? query.getPageSize() : 10;
        int total = dataList.size();
        int fromIndex = (current - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);
        
        List<UserDictDataEntity> pageList = new java.util.ArrayList<>();
        if (fromIndex < total) {
            pageList = dataList.subList(fromIndex, toIndex);
        }

        PageResp<UserDictDataEntity> pageResp = PageResp.of(pageList, (long) total);
        return ApiResponse.success(pageResp);
    }

    @GetMapping("/{id}")
    public ApiResponse<UserDictDataEntity> getById(@PathVariable("id") Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<UserDictDataEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserDictDataEntity::getId, id);
        queryWrapper.in(UserDictDataEntity::getUserId, userId, 0L);
        return ApiResponse.success(userDictDataMapper.selectOne(queryWrapper));
    }

    @PostMapping
    public ApiResponse<Boolean> insert(@RequestBody UserDictDataEntity entity) {
        Long userId = StpUtil.getLoginIdAsLong();
        userDictDataService.createDictData(entity, userId);
        return ApiResponse.success(true);
    }

    @PutMapping
    public ApiResponse<Boolean> update(@RequestBody UserDictDataEntity entity) {
        Long userId = StpUtil.getLoginIdAsLong();
        userDictDataService.updateDictData(entity.getId(), entity, userId);
        return ApiResponse.success(true);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable("id") Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        userDictDataService.deleteDictData(id, userId);
        return ApiResponse.success(true);
    }

    // ================= 管理员 API =================

    @cn.dev33.satoken.annotation.SaCheckRole("admin")
    @PostMapping("/admin/query")
    public ApiResponse<PageResp<UserDictDataEntity>> adminQuery(@RequestBody CommonQuery<UserDictDataEntity> query) {
        LambdaQueryWrapper<UserDictDataEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        
        // 强制只能查询基础值 (userId = 0)
        lambdaQueryWrapper.eq(UserDictDataEntity::getUserId, 0L);

        UserDictDataEntity condition = query.getCondition();
        if (condition != null) {
            lambdaQueryWrapper.eq(SysUtil.isNotEmpty(condition.getDictType()),
                    UserDictDataEntity::getDictType, condition.getDictType());
            lambdaQueryWrapper.like(SysUtil.isNotEmpty(condition.getDictLabel()),
                    UserDictDataEntity::getDictLabel, condition.getDictLabel());
            lambdaQueryWrapper.eq(SysUtil.isNotEmpty(condition.getStatus()),
                    UserDictDataEntity::getStatus, condition.getStatus());
        }
        lambdaQueryWrapper.orderByAsc(UserDictDataEntity::getDictType, UserDictDataEntity::getDictSort);

        Page<UserDictDataEntity> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<UserDictDataEntity> iPage = userDictDataMapper.selectPage(page, lambdaQueryWrapper);
        PageResp<UserDictDataEntity> pageResp = PageResp.of(iPage.getRecords(), iPage.getTotal());
        return ApiResponse.success(pageResp);
    }

    @cn.dev33.satoken.annotation.SaCheckRole("admin")
    @PostMapping("/admin")
    public ApiResponse<Boolean> adminInsert(@RequestBody UserDictDataEntity entity) {
        // userId = 0L 代表基础值
        entity.setUserId(0L);
        entity.fillCreateCommonField(0L);
        boolean b = userDictDataService.save(entity);
        return ApiResponse.success(b);
    }

    @cn.dev33.satoken.annotation.SaCheckRole("admin")
    @PutMapping("/admin/{id}")
    public ApiResponse<Boolean> adminUpdate(@PathVariable("id") Long id, @RequestBody UserDictDataEntity entity) {
        UserDictDataEntity target = userDictDataService.getById(id);
        if (target == null || target.getUserId() != 0L) {
            throw new RuntimeException("只能修改基础值");
        }
        entity.setId(id);
        entity.fillUpdateCommonField(0L);
        boolean b = userDictDataService.updateById(entity);
        return ApiResponse.success(b);
    }

    @cn.dev33.satoken.annotation.SaCheckRole("admin")
    @DeleteMapping("/admin/{id}")
    public ApiResponse<Boolean> adminDelete(@PathVariable("id") Long id) {
        UserDictDataEntity target = userDictDataService.getById(id);
        if (target == null || target.getUserId() != 0L) {
            throw new RuntimeException("只能删除基础值");
        }
        boolean b = userDictDataService.removeById(id);
        return ApiResponse.success(b);
    }
}
