package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;
import top.aiolife.record.pojo.entity.HonorRecordEntity;

/**
 * 荣誉记录服务接口
 *
 * @author Lys
 * @date 2026/04/11
 */
public interface IHonorRecordService extends IService<HonorRecordEntity> {

    List<HonorRecordEntity> listForUser(long userId);

    HonorRecordEntity getOwned(long userId, long id);

    HonorRecordEntity createForUser(long userId, HonorRecordEntity entity);

    HonorRecordEntity updateForUser(long userId, HonorRecordEntity entity);

    void deleteForUser(long userId, List<String> ids);

    void toggleTop(long userId, long id);
}
