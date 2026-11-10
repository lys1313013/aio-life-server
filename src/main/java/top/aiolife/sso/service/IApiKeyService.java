package top.aiolife.sso.service;

import com.baomidou.mybatisplus.extension.service.IService;
import top.aiolife.sso.pojo.entity.ApiKeyEntity;

import java.util.List;

/**
 * API Key Service
 *
 * @author Lys
 * @date 2026/03/09
 */
public interface IApiKeyService extends IService<ApiKeyEntity> {

    /** 仅修改当前用户持有且未删除的密钥备注。 */
    ApiKeyEntity updateRemark(Long userId, Long id, String remark);
    
    /**
     * 生成 API Key
     *
     * @param userId 用户ID
     * @param remark 备注
     * @param expireDays 过期天数 (null 为永不过期)
     * @return 生成的 API Key 实体
     */
    ApiKeyEntity generateApiKey(Long userId, String remark, Integer expireDays);

    /**
     * 获取用户的所有 API Key
     *
     * @param userId 用户ID
     * @return API Key 列表
     */
    List<ApiKeyEntity> listByUserId(Long userId);

    /**
     * 根据 API Key 字符串获取实体
     *
     * @param apiKey API Key 字符串
     * @return API Key 实体
     */
    ApiKeyEntity getByApiKey(String apiKey);
}
