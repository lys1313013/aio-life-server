package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import top.aiolife.record.pojo.entity.UserBindEntity;
import top.aiolife.record.pojo.vo.DoubanAccountVO;

import java.util.List;

/**
 * 用户绑定服务接口
 */
public interface IUserBindService extends IService<UserBindEntity> {

    /**
     * 获取用户的所有绑定信息
     * @param userId 用户ID
     * @return 绑定列表
     */
    List<UserBindEntity> getBindsByUserId(Long userId);

    /**
     * 获取用户指定平台的绑定信息
     * @param userId 用户ID
     * @param platform 平台类型
     * @return 绑定信息
     */
    UserBindEntity getBindByUserIdAndPlatform(Long userId, String platform);

    /** 查询当前用户绑定列表，按需隐藏凭证；微信读书凭证始终隐藏。 */
    List<UserBindEntity> listForUser(long userId, Boolean includeToken);

    /** 根据豆瓣账号 ID 查询公开主页昵称，仅用于辅助用户确认账号。 */
    DoubanAccountVO verifyDoubanAccount(String accountId);

    boolean createForUser(long userId, UserBindEntity entity);

    boolean updateForUser(long userId, UserBindEntity entity);

    boolean deleteForUser(long userId, long id);
}
