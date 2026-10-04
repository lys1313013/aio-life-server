package top.aiolife.record.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import top.aiolife.record.pojo.entity.AnniversaryRecordEntity;

/**
 * 纪念日 Service
 *
 * @author Lys
 * @date 2026/04/18
 */
public interface IAnniversaryRecordService extends IService<AnniversaryRecordEntity> {
    AnniversaryRecordEntity createForUser(long userId, AnniversaryRecordEntity entity);

    AnniversaryRecordEntity updateForUser(long userId, AnniversaryRecordEntity entity);

    AnniversaryRecordEntity setPinned(long userId, long id, int isPinned);

    void reorderPinned(long userId, List<Long> ids);

    void deleteForUser(long userId, List<String> ids);
}
