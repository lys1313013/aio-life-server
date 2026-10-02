package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.constant.StatusConst;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.mapper.ITaskColumnMapper;
import top.aiolife.record.pojo.entity.TaskColumnEntity;
import top.aiolife.record.pojo.query.TaskColumnQuery;
import top.aiolife.record.pojo.req.TaskColumnCreateReq;
import top.aiolife.record.pojo.req.TaskColumnSortReq;
import top.aiolife.record.pojo.req.TaskColumnUpdateReq;
import top.aiolife.record.pojo.vo.TaskColumnVO;
import top.aiolife.record.service.ITaskColumnService;

/**
 * 任务栏控制器
 *
 * @author Lys
 * @date 2025/04/12 14:36
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/taskColumn")
public class TaskColumnController {

    private ITaskColumnMapper taskColumnMapper;

    private ITaskColumnService taskColumnService;

    public ITaskColumnMapper getBaseMapper() {
        return taskColumnMapper;
    }

    @GetMapping("/query")
    public ApiResponse<PageResp<TaskColumnVO>> query(
            @QueryParams CommonQuery<TaskColumnQuery> requestQuery) {
        CommonQuery<TaskColumnEntity> query = requestQuery.map(RecordApiConvertor.INSTANCE::fromTaskColumnQuery);
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<TaskColumnEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(TaskColumnEntity::getUserId, userId);
        lambdaQueryWrapper.eq(TaskColumnEntity::getIsDeleted, StatusConst.NO_DELETE);
        TaskColumnEntity condition = query.getCondition();
        lambdaQueryWrapper.orderByAsc(TaskColumnEntity::getSortOrder);        // 分页
        Page<TaskColumnEntity> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<TaskColumnEntity> iPage = getBaseMapper().selectPage(page, lambdaQueryWrapper);
        PageResp<TaskColumnEntity> objectPageResp = PageResp.of(iPage.getRecords(), iPage.getTotal());
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toTaskColumnVOPage(objectPageResp));
    }

    /**
     * 插入或更新
     *
     * @param entity
     */
    @PostMapping
    public ApiResponse<TaskColumnVO> save(@Valid @RequestBody TaskColumnCreateReq entityReq) {
        TaskColumnEntity entity = RecordApiConvertor.INSTANCE.fromTaskColumnCreateReq(entityReq);
        long userId = StpUtil.getLoginIdAsLong();
        // 查询当前最大的sort_order
        LambdaQueryWrapper<TaskColumnEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TaskColumnEntity::getUserId, userId);
        queryWrapper.orderByDesc(TaskColumnEntity::getSortOrder);
        queryWrapper.last("limit 1");
        TaskColumnEntity taskColumnEntity = getBaseMapper().selectOne(queryWrapper);
        int maxOrder = (taskColumnEntity != null && taskColumnEntity.getSortOrder() != null) ? taskColumnEntity.getSortOrder() : 0;
        entity.setSortOrder(maxOrder + 1);

        entity.setId(null);
        entity.setUserId(userId);
        entity.fillCreateCommonField(userId);
        getBaseMapper().insertOrUpdate(entity);

        return ApiResponse.success(RecordApiConvertor.INSTANCE.toTaskColumnVO(entity));
    }

    /**
     * 更新
     *
     * @param entity
     */
    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(@PathVariable("id") Long id, @Valid @RequestBody TaskColumnUpdateReq entityReq) {
        TaskColumnEntity entity = RecordApiConvertor.INSTANCE.fromTaskColumnUpdateReq(entityReq);
        Long userId = StpUtil.getLoginIdAsLong();
        entity.setId(id);
        entity.setUserId(userId);
        entity.fillUpdateCommonField(userId);

        LambdaQueryWrapper<TaskColumnEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskColumnEntity::getId, id);
        wrapper.eq(TaskColumnEntity::getUserId, userId);

        getBaseMapper().update(entity, wrapper);
        return ApiResponse.success();
    }

    /**
     * 删除
     *
     * @param entity id
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        LambdaQueryWrapper<TaskColumnEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskColumnEntity::getId, id);
        wrapper.eq(TaskColumnEntity::getUserId, StpUtil.getLoginIdAsLong());
        getBaseMapper().delete(wrapper);
        return ApiResponse.success();
    }

    /**
     * 拖拽排序
     *
     * @param list 只传id和sortOrder
     */
    @PostMapping("/reSort")
    public ApiResponse<Void> reSort(@Valid @RequestBody List<TaskColumnSortReq> requests) {
        List<TaskColumnEntity> list = requests.stream().map(RecordApiConvertor.INSTANCE::fromTaskColumnSortReq).toList();
        Long userId = StpUtil.getLoginIdAsLong();
        for (TaskColumnEntity entity : list) {
            TaskColumnEntity update = new TaskColumnEntity();
            update.setSortOrder(entity.getSortOrder());
            update.fillUpdateCommonField(userId);
            LambdaQueryWrapper<TaskColumnEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TaskColumnEntity::getId, entity.getId());
            wrapper.eq(TaskColumnEntity::getUserId, userId);
            getBaseMapper().update(update, wrapper);
        }
        return ApiResponse.success();
    }
}
