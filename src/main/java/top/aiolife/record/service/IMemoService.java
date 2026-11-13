package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.pojo.entity.MemoEntity;

/**
 * 备忘录Service接口
 *
 * @author Lys
 * @date 2025/12/07 14:35
 */
public interface IMemoService extends IService<MemoEntity> {

    PageResp<MemoEntity> pageForUser(long userId, String content, int page, int pageSize);

    MemoEntity getOwned(long userId, long id);

    MemoEntity createForUser(long userId, MemoEntity entity);

    void updateForUser(long userId, long id, MemoEntity entity);

    void deleteForUser(long userId, long id);
}
