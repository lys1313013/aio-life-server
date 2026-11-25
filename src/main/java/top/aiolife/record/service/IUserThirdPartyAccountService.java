package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import top.aiolife.record.pojo.entity.UserThirdPartyAccountEntity;
import top.aiolife.record.pojo.req.UserThirdPartyAccountVerifyReq;
import top.aiolife.record.pojo.vo.UserThirdPartyAccountVerifyVO;

import java.util.List;

/**
 * 用户绑定服务接口
 */
public interface IUserThirdPartyAccountService extends IService<UserThirdPartyAccountEntity> {

    /**
     * 获取用户的所有绑定信息
     * @param userId 用户ID
     * @return 绑定列表
     */
    List<UserThirdPartyAccountEntity> getBindsByUserId(Long userId);

    /**
     * 获取用户指定平台的绑定信息
     * @param userId 用户ID
     * @param platform 平台类型
     * @return 绑定信息
     */
    UserThirdPartyAccountEntity getBindByUserIdAndPlatform(Long userId, String platform);

    /** 查询当前用户绑定列表，按需隐藏凭证；微信读书凭证始终隐藏。 */
    List<UserThirdPartyAccountEntity> listForUser(long userId, Boolean includeToken);

    /** 验证当前表单配置，不写入绑定。 */
    UserThirdPartyAccountVerifyVO verifyForUser(long userId, UserThirdPartyAccountVerifyReq request);

    boolean createForUser(long userId, UserThirdPartyAccountEntity entity);

    boolean updateForUser(long userId, UserThirdPartyAccountEntity entity);

    boolean deleteForUser(long userId, long id);
}
