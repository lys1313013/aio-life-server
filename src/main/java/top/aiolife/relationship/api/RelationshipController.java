package top.aiolife.relationship.api;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.relationship.pojo.entity.PersonRelationship;
import top.aiolife.relationship.pojo.entity.PersonWithRelationships;
import top.aiolife.relationship.pojo.entity.RelatesToRelationship;
import top.aiolife.relationship.pojo.req.PersonReq;
import top.aiolife.relationship.pojo.req.RelationshipReq;
import top.aiolife.relationship.service.IPersonService;
import top.aiolife.relationship.service.IRelationshipService;

import java.util.Map;

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
    public ApiResponse<Map<String, Object>> getGraphData() {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(personService.getGraphData(userId));
    }

    // ==================== 人物接口 ====================

    /**
     * 获取所有人物
     */
    @GetMapping("/persons")
    public ApiResponse<Object> getAllPersons() {
        long userId = StpUtil.getLoginIdAsLong();
        // 返回图谱数据，前端可以从这里获取节点和边
        return ApiResponse.success(personService.getGraphData(userId));
    }

    /**
     * 获取人物详情
     */
    @GetMapping("/persons/{id}")
    public ApiResponse<PersonWithRelationships> getPerson(@PathVariable String id) {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(personService.getPersonWithRelationships(userId, id));
    }

    /**
     * 搜索人物
     */
    @GetMapping("/persons/search")
    public ApiResponse<Object> searchPersons(@RequestParam String keyword) {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(personService.searchPersons(userId, keyword));
    }

    /**
     * 创建人物
     */
    @PostMapping("/persons")
    public ApiResponse<PersonRelationship> createPerson(@RequestBody PersonReq req) {
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
        return ApiResponse.success(personService.createPerson(person));
    }

    /**
     * 更新人物
     */
    @PutMapping("/persons/{id}")
    public ApiResponse<PersonRelationship> updatePerson(@PathVariable String id, @RequestBody PersonReq req) {
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
        return ApiResponse.success(personService.updatePerson(userId, person));
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
    public ApiResponse<RelatesToRelationship> createRelationship(@RequestBody RelationshipReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        RelatesToRelationship relationship = new RelatesToRelationship();
        relationship.setRelationType(req.getRelationType());
        relationship.setDirection(req.getDirection());
        relationship.setDescription(req.getDescription());
        relationship.setTags(req.getTags());
        return ApiResponse.success(relationshipService.createRelationship(relationship, userId, req.getSourcePersonId(), req.getTargetPersonId()));
    }

    /**
     * 更新关系
     */
    @PutMapping("/{id}")
    public ApiResponse<RelatesToRelationship> updateRelationship(@PathVariable Long id, @RequestBody RelationshipReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        RelatesToRelationship relationship = new RelatesToRelationship();
        relationship.setRelationType(req.getRelationType());
        relationship.setDirection(req.getDirection());
        relationship.setDescription(req.getDescription());
        relationship.setTags(req.getTags());
        return ApiResponse.success(relationshipService.updateRelationship(userId, id, relationship));
    }

    /**
     * 删除关系
     */
    @DeleteMapping
    public ApiResponse<Void> deleteRelationship(@RequestBody RelationshipReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        relationshipService.deleteRelationship(userId, req.getSourcePersonId(), req.getTargetPersonId());
        return ApiResponse.success();
    }
}
