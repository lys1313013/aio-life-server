package top.aiolife.relationship.api;

import cn.dev33.satoken.stp.StpUtil;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.relationship.convertor.RelationshipApiConvertor;
import top.aiolife.relationship.pojo.entity.PersonRelationship;
import top.aiolife.relationship.pojo.entity.RelatesToRelationship;
import top.aiolife.relationship.pojo.req.PersonReq;
import top.aiolife.relationship.pojo.req.RelationshipDeleteReq;
import top.aiolife.relationship.pojo.req.RelationshipReq;
import top.aiolife.relationship.pojo.req.RelationshipUpdateReq;
import top.aiolife.relationship.pojo.vo.*;
import top.aiolife.relationship.service.IPersonService;
import top.aiolife.relationship.service.IRelationshipService;

/**
 * 关系图谱 Controller
 */
@RestController
@RequestMapping("/relationships")
@ConditionalOnProperty(name = "aio.life.neo4j.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class RelationshipController {

    private final IPersonService personService;
    private final IRelationshipService relationshipService;

    // ==================== 图谱接口 ====================

    /**
     * 获取图谱数据（节点和边）
     */
    @GetMapping("/graph")
    public ApiResponse<GraphVO> getGraphData() {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(RelationshipApiConvertor.INSTANCE.toGraphVO(personService.getGraphData(userId)));
    }

    // ==================== 人物接口 ====================

    /**
     * 获取所有人物
     */
    @GetMapping("/persons")
    public ApiResponse<GraphVO> getAllPersons() {
        long userId = StpUtil.getLoginIdAsLong();
        // 返回图谱数据，前端可以从这里获取节点和边
        return ApiResponse.success(RelationshipApiConvertor.INSTANCE.toGraphVO(personService.getGraphData(userId)));
    }

    /**
     * 获取人物详情
     */
    @GetMapping("/persons/{id}")
    public ApiResponse<PersonDetailVO> getPerson(@PathVariable String id) {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(RelationshipApiConvertor.INSTANCE.toPersonDetailVO(personService.getPersonWithRelationships(userId, id)));
    }

    /**
     * 搜索人物
     */
    @GetMapping("/persons/search")
    public ApiResponse<List<PersonVO>> searchPersons(@RequestParam String keyword) {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(RelationshipApiConvertor.INSTANCE.toPersonVOList(personService.searchPersons(userId, keyword)));
    }

    /**
     * 创建人物
     */
    @PostMapping("/persons")
    public ApiResponse<PersonVO> createPerson(@RequestBody PersonReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        PersonRelationship person = new PersonRelationship();
        person.setUserId(userId);
        person.setName(req.getName());
        person.setAvatar(req.getAvatar());
        person.setCategory(req.getCategory());
        person.setDescription(req.getDescription());
        person.setTags(req.getTags());
        person.setBirthday(req.getBirthday());
        person.setPhone(req.getPhone());
        person.setEmail(req.getEmail());
        person.setSchool(req.getSchool());
        person.setSocialLinks(req.getSocialLinks());
        person.setNotes(req.getNotes());
        return ApiResponse.success(RelationshipApiConvertor.INSTANCE.toPersonVO(personService.createPerson(person)));
    }

    /**
     * 更新人物
     */
    @PutMapping("/persons/{id}")
    public ApiResponse<PersonVO> updatePerson(@PathVariable String id, @RequestBody PersonReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        PersonRelationship person = new PersonRelationship();
        person.setId(id);
        person.setUserId(userId);
        person.setName(req.getName());
        person.setAvatar(req.getAvatar());
        person.setCategory(req.getCategory());
        person.setDescription(req.getDescription());
        person.setTags(req.getTags());
        person.setBirthday(req.getBirthday());
        person.setPhone(req.getPhone());
        person.setEmail(req.getEmail());
        person.setSchool(req.getSchool());
        person.setSocialLinks(req.getSocialLinks());
        person.setNotes(req.getNotes());
        return ApiResponse.success(RelationshipApiConvertor.INSTANCE.toPersonVO(personService.updatePerson(userId, person)));
    }

    /**
     * 删除人物
     */
    @DeleteMapping("/persons/{id}")
    public ApiResponse<Void> deletePerson(@PathVariable String id) {
        long userId = StpUtil.getLoginIdAsLong();
        personService.deletePerson(userId, id);
        return ApiResponse.success();
    }

    // ==================== 关系接口 ====================

    /**
     * 创建关系
     */
    @PostMapping
    public ApiResponse<RelationshipVO> createRelationship(@RequestBody RelationshipReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        RelatesToRelationship relationship = new RelatesToRelationship();
        relationship.setRelationType(req.getRelationType());
        relationship.setDirection(req.getDirection());
        relationship.setDescription(req.getDescription());
        relationship.setTags(req.getTags());
        return ApiResponse.success(RelationshipApiConvertor.INSTANCE.toRelationshipVO(relationshipService.createRelationship(relationship, userId, req.getSourcePersonId(), req.getTargetPersonId())));
    }

    /**
     * 更新关系
     */
    @PutMapping("/{id}")
    public ApiResponse<RelationshipVO> updateRelationship(@PathVariable Long id, @RequestBody RelationshipUpdateReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        RelatesToRelationship relationship = new RelatesToRelationship();
        relationship.setRelationType(req.getRelationType());
        relationship.setDirection(req.getDirection());
        relationship.setDescription(req.getDescription());
        relationship.setTags(req.getTags());
        return ApiResponse.success(RelationshipApiConvertor.INSTANCE.toRelationshipVO(relationshipService.updateRelationship(userId, id, relationship)));
    }

    /**
     * 删除关系
     */
    @DeleteMapping
    public ApiResponse<Void> deleteRelationship(@RequestBody RelationshipDeleteReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        relationshipService.deleteRelationship(userId, req.getSourcePersonId(), req.getTargetPersonId());
        return ApiResponse.success();
    }
}
