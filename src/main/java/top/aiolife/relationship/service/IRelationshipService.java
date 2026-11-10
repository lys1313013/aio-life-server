package top.aiolife.relationship.service;

import top.aiolife.relationship.pojo.entity.RelatesToRelationship;

/**
 * 关系 Service 接口
 */
public interface IRelationshipService {

    /**
     * 创建关系
     */
    RelatesToRelationship createRelationship(RelatesToRelationship relationship, Long userId, String sourcePersonId, String targetPersonId);

    /**
     * 更新关系
     */
    RelatesToRelationship updateRelationship(Long userId, String relationshipId, RelatesToRelationship relationship);

    /**
     * 删除关系
     */
    void deleteRelationship(Long userId, String sourcePersonId, String targetPersonId);
}
