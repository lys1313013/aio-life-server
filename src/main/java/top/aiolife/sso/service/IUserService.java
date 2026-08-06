package top.aiolife.sso.service;

import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.resq.PageResp;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.pojo.req.ChangePasswordReq;
import top.aiolife.sso.pojo.req.LoginReq;
import top.aiolife.sso.pojo.req.RegisterReq;
import top.aiolife.sso.pojo.req.ResetPasswordReq;
import top.aiolife.sso.pojo.vo.UserBasicInfoVO;
import top.aiolife.sso.pojo.vo.UserInfoVO;
import top.aiolife.sso.pojo.vo.UserLoginVO;
import top.aiolife.sso.pojo.vo.UserVO;

import java.util.List;

/**
 * 用户服务接口
 *
 * @author Lys
 * @date 2025/12/06 23:58
 */
public interface IUserService {

    /**
     * 登录
     *
     * @param loginReq 登录参数
     * @param ip       客户端IP
     * @return 登录结果
     */
    UserLoginVO login(LoginReq loginReq, String ip);

    /**
     * 注册
     *
     * @param registerReq 注册参数
     */
    void register(RegisterReq registerReq);

    /**
     * 发送注册验证码
     *
     * @param email 邮箱
     * @param ip    客户端IP
     */
    void sendRegisterCode(String email, String ip);

    /**
     * 发送重置密码验证码
     *
     * @param email 邮箱
     * @param ip    客户端IP
     */
    void sendResetPasswordCode(String email, String ip);

    /**
     * 重置密码
     *
     * @param resetPasswordReq 重置密码参数
     */
    void resetPassword(ResetPasswordReq resetPasswordReq);

    /**
     * 获取用户信息
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    UserInfoVO getUserInfo(long userId);

    /**
     * 获取用户基本信息
     *
     * @param userId 用户ID
     * @return 用户基本信息
     */
    UserBasicInfoVO getUserBasicInfo(long userId);

    void updateUser(UserEntity userEntity);

    /**
     * 修改密码
     */
    void changePassword(long userId, ChangePasswordReq changePasswordReq);

    /**
     * 获取用户列表
     */
    PageResp<UserVO> getUserList(CommonQuery query);

    /**
     * 新增用户
     */
    void addUser(UserEntity userEntity);

    /**
     * 删除用户
     */
    void deleteUser(Long id);

    /**
     * 是否已设置二级密码
     */
    boolean hasSecondaryPassword(long userId);

    /**
     * 设置二级密码
     */
    void setSecondaryPassword(long userId, String password, String oldPassword);

    /**
     * 验证二级密码，返回解锁 key
     */
    String verifySecondaryPassword(long userId, String password, String menuPath);

    /**
     * 获取用户锁定的菜单 ID 列表
     */
    List<Long> getSecondaryLockMenuIds(long userId);

    /**
     * 保存用户锁定的菜单 ID 列表（需验证二级密码）
     */
    void saveSecondaryLockMenus(long userId, List<Long> menuIds, String secondaryPassword);

    /**
     * 发送重置二级密码验证码（使用当前登录用户的邮箱）
     */
    void sendResetSecondaryPasswordCode(long userId, String ip);

    /**
     * 重置二级密码（使用当前登录用户的邮箱验证码）
     */
    void resetSecondaryPassword(long userId, String code, String password);
}
