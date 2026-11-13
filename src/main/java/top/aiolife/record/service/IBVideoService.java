package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;
import java.util.Map;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.pojo.entity.BVideoEntity;
import top.aiolife.record.pojo.vo.BVideoCoverVO;
import top.aiolife.record.pojo.vo.BVideoStatisticsVO;

/**
 * 哔哩哔哩学习视频 Service
 *
 * @author Lys
 * @date 2026/11/13
 */
public interface IBVideoService extends IService<BVideoEntity> {

    PageResp<BVideoEntity> pageForUser(long userId, BVideoEntity condition, int page, int pageSize);

    BVideoEntity getOwned(long userId, long id);

    BVideoEntity findByBvid(long userId, String bvid);

    void createForUser(long userId, BVideoEntity entity);

    boolean updateForUser(long userId, long id, BVideoEntity entity);

    boolean deleteForUser(long userId, long id);

    void tagForUser(long userId, BVideoEntity entity);

    void syncProgress(long userId, BVideoEntity entity);

    List<BVideoCoverVO> listCovers(long userId, List<Long> ids);

    void retryCover(long userId, long id);

    Map<String, Integer> statusCount(long userId);

    BVideoStatisticsVO statistics(long userId);
}
