package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;
import top.aiolife.record.pojo.entity.MilestoneEntity;

/**
 * 里程碑服务接口
 *
 * @author Lys
 * @date 2026-02-07 16:43
 */
public interface IMilestoneService extends IService<MilestoneEntity> {

    List<MilestoneEntity> listForUser(long userId);

    MilestoneEntity createForUser(long userId, MilestoneEntity entity);

    MilestoneEntity updateForUser(long userId, MilestoneEntity entity);

    void deleteForUser(long userId, List<String> ids);
}
