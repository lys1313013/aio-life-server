package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.pojo.entity.ThoughtEntity;

/**
 * 闪念服务接口。
 */
public interface IThoughtService extends IService<ThoughtEntity> {

    PageResp<ThoughtEntity> pageForUser(long userId, ThoughtEntity condition, int page, int pageSize);

    void updateForUser(long userId, long id, ThoughtEntity entity);

    void deleteForUser(long userId, List<String> ids);

    List<ThoughtEntity> listPinnedForUser(long userId);
}
