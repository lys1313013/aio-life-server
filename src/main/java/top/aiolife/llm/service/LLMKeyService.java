package top.aiolife.llm.service;

import top.aiolife.llm.pojo.entity.LLMKeyEntity;

import java.util.List;

public interface LLMKeyService {

    /**
     * 保存大模型配置
     * @param llmKeyEntity 大模型配置实体
     */
    void saveLLMKey(LLMKeyEntity llmKeyEntity);

    /**
     * 更新大模型配置
     * @param llmKeyEntity 大模型配置实体
     */
    void updateLLMKey(LLMKeyEntity llmKeyEntity);

    /**
     * 删除大模型配置
     * @param id 配置ID
     * @param userId 用户ID
     */
    void deleteLLMKey(Long id, Long userId);

    /**
     * 根据用户ID获取大模型配置列表
     * @param userId 用户ID
     * @return 大模型配置列表
     */
    List<LLMKeyEntity> getLLMKeyList(Long userId);

    /**
     * 根据用户ID获取默认大模型配置
     * @param userId 用户ID
     * @return 默认大模型配置
     */
    LLMKeyEntity getDefaultLLMKey(Long userId);

    /**
     * 设置默认大模型配置
     * @param id 配置ID
     * @param userId 用户ID
     */
    void setDefaultLLMKey(Long id, Long userId);
}
