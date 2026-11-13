package top.aiolife.record.service;

import java.util.List;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.pojo.entity.TaskColumnEntity;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/04/12 20:19
 */
public interface ITaskColumnService {

    void updateBatchById(List<TaskColumnEntity> list);

    PageResp<TaskColumnEntity> pageForUser(long userId, int page, int pageSize);

    TaskColumnEntity createForUser(long userId, TaskColumnEntity entity);

    void updateForUser(long userId, long id, TaskColumnEntity entity);

    void deleteForUser(long userId, long id);

    void reSort(long userId, List<TaskColumnEntity> list);
}
