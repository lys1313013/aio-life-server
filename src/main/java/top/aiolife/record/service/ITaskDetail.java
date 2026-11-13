package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;
import java.util.Map;
import top.aiolife.record.pojo.entity.TaskDetailEntity;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2026-02-07 23:23
 */
public interface ITaskDetail extends IService<TaskDetailEntity> {

    /**
     * 获取未完成任务数
     *
     * @param taskIdList 任务id列表，为空则直接返回空结果
     * @param userId 用户id
     * @return 任务id与未完成数的映射
     */
    Map<Long, Integer> getUnCompletedCount(List<Long> taskIdList, Long userId);

    /** 查询指定主任务的子任务，按完成状态、优先级和排序展示。 */
    List<TaskDetailEntity> listForTask(long userId, long taskId);

    void reSort(long userId, List<TaskDetailEntity> list);

    TaskDetailEntity createForUser(long userId, TaskDetailEntity entity);

    boolean updateForUser(long userId, TaskDetailEntity entity);

    void deleteForUser(long userId, long id);

    boolean setStarred(long userId, long id, int isStarred);

    /** 查询当前用户全部未完成的关注子任务，并填充所属主任务名称。 */
    List<TaskDetailEntity> listWatched(long userId);
}
