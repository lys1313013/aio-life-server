package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.pojo.entity.TaskEntity;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/04/12 21:01
 */
public interface ITaskService extends IService<TaskEntity> {
    void updateBatchById(List<TaskEntity> list);

    /** 分页查询当前用户的主任务，并填充未完成子任务数。 */
    PageResp<TaskEntity> pageForUser(long userId, Long taskId, int page, int pageSize);

    TaskEntity createForUser(long userId, TaskEntity entity);

    void updateForUser(long userId, long id, TaskEntity entity);

    void deleteForUser(long userId, long id);

    void reSort(long userId, List<TaskEntity> list);

    /** 获取当前用户拥有的主任务，不存在或无权限时抛出业务异常。 */
    TaskEntity getOwned(long userId, long id);
}
