package top.aiolife.system.service;

import top.aiolife.system.pojo.req.SystemConfigUpdateReq;
import top.aiolife.system.pojo.vo.SystemConfigVO;

import java.util.List;

/**
 * 系统配置服务接口
 *
 * @author Ethan
 * @date 2026/07/19
 */
public interface ISystemConfigService {

    /**
     * 按 key 前缀筛选配置列表
     *
     * @param keyPrefix key 前缀，为空则返回全部
     * @return 配置列表
     */
    List<SystemConfigVO> list(String keyPrefix);

    /**
     * 获取单项配置
     *
     * @param key 配置键
     * @return 配置 VO；不存在返回 null
     */
    SystemConfigVO getByKey(String key);

    /**
     * 获取单项配置，不存在时抛出业务异常。
     *
     * @param key 配置键
     * @return 配置 VO
     */
    SystemConfigVO getRequiredByKey(String key);

    /**
     * 获取单项配置的原始值（业务代码内部使用）
     *
     * @param key 配置键
     * @return 配置值字符串；不存在返回 null
     */
    String getValueByKey(String key);

    /**
     * 更新单项配置
     *
     * @param key       配置键
     * @param req       更新请求
     * @param operatorId 操作人 ID
     * @return 更新后的 VO
     */
    SystemConfigVO update(String key, SystemConfigUpdateReq req, long operatorId);
}
