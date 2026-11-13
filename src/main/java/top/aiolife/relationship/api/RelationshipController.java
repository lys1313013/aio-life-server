package top.aiolife.relationship.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
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
 * 关系图谱控制器。
 *
 * @author Lys
 * @date 2026/11/13
 */
@Tag(name = "人际关系")
@RestController
@RequestMapping("/relationships")
@ConditionalOnProperty(name = "aio.life.neo4j.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class RelationshipController {

    private final IPersonService personService;
    private final IRelationshipService relationshipService;

    @Operation(summary = "查询人际关系图谱")
    @GetMapping("/graph")
    public ApiResponse<GraphVO> getGraphData(@CurrentUserId String userId) {
        return ApiResponse.success(RelationshipApiConvertor.INSTANCE.toGraphVO(
                personService.getGraphData(IdCodec.decode(userId))));
    }

    @Operation(summary = "查询人际关系人物")
    @GetMapping("/persons")
    public ApiResponse<GraphVO> getAllPersons(@CurrentUserId String userId) {
        return ApiResponse.success(RelationshipApiConvertor.INSTANCE.toGraphVO(
                personService.getGraphData(IdCodec.decode(userId))));
    }

    @Operation(summary = "查询人物详情")
    @GetMapping("/persons/{id}")
    public ApiResponse<PersonDetailVO> getPerson(@CurrentUserId String userId, @PathVariable String id) {
        return ApiResponse.success(RelationshipApiConvertor.INSTANCE.toPersonDetailVO(
                personService.getPersonWithRelationships(IdCodec.decode(userId), id)));
    }

    @Operation(summary = "搜索人际关系人物")
    @GetMapping("/persons/search")
    public ApiResponse<List<PersonVO>> searchPersons(@CurrentUserId String userId,
                                                     @RequestParam String keyword) {
        return ApiResponse.success(RelationshipApiConvertor.INSTANCE.toPersonVOList(
                personService.searchPersons(IdCodec.decode(userId), keyword)));
    }

    @Operation(summary = "新增人际关系人物")
    @PostMapping("/persons")
    public ApiResponse<PersonVO> createPerson(@CurrentUserId String userId,
                                              @Valid @RequestBody PersonReq req) {
        PersonRelationship person = toPerson(req);
        person.setUserId(IdCodec.decode(userId));
        return ApiResponse.success(RelationshipApiConvertor.INSTANCE.toPersonVO(
                personService.createPerson(person)));
    }

    @Operation(summary = "更新人际关系人物")
    @PutMapping("/persons/{id}")
    public ApiResponse<PersonVO> updatePerson(@CurrentUserId String userId, @PathVariable String id,
                                              @Valid @RequestBody PersonReq req) {
        PersonRelationship person = toPerson(req);
        person.setId(id);
        person.setUserId(IdCodec.decode(userId));
        return ApiResponse.success(RelationshipApiConvertor.INSTANCE.toPersonVO(
                personService.updatePerson(IdCodec.decode(userId), person)));
    }

    @Operation(summary = "删除人际关系人物")
    @DeleteMapping("/persons/{id}")
    public ApiResponse<Void> deletePerson(@CurrentUserId String userId, @PathVariable String id) {
        personService.deletePerson(IdCodec.decode(userId), id);
        return ApiResponse.success();
    }

    @Operation(summary = "新增人物关系")
    @PostMapping
    public ApiResponse<RelationshipVO> createRelationship(@CurrentUserId String userId,
                                                          @Valid @RequestBody RelationshipReq req) {
        RelatesToRelationship relationship = toRelationship(req.getRelationType(), req.getDirection(),
                req.getDescription(), req.getTags());
        return ApiResponse.success(RelationshipApiConvertor.INSTANCE.toRelationshipVO(
                relationshipService.createRelationship(relationship, IdCodec.decode(userId),
                        req.getSourcePersonId(), req.getTargetPersonId())));
    }

    @Operation(summary = "更新人物关系")
    @PutMapping("/{id}")
    public ApiResponse<RelationshipVO> updateRelationship(@CurrentUserId String userId, @PathVariable String id,
                                                          @Valid @RequestBody RelationshipUpdateReq req) {
        RelatesToRelationship relationship = toRelationship(req.getRelationType(), req.getDirection(),
                req.getDescription(), req.getTags());
        return ApiResponse.success(RelationshipApiConvertor.INSTANCE.toRelationshipVO(
                relationshipService.updateRelationship(IdCodec.decode(userId), id, relationship)));
    }

    @Operation(summary = "删除人物关系")
    @DeleteMapping
    public ApiResponse<Void> deleteRelationship(@CurrentUserId String userId,
                                                @Valid @RequestBody RelationshipDeleteReq req) {
        relationshipService.deleteRelationship(IdCodec.decode(userId),
                req.getSourcePersonId(), req.getTargetPersonId());
        return ApiResponse.success();
    }

    private PersonRelationship toPerson(PersonReq req) {
        PersonRelationship person = new PersonRelationship();
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
        return person;
    }

    private RelatesToRelationship toRelationship(String relationType, String direction,
                                                 String description, String tags) {
        RelatesToRelationship relationship = new RelatesToRelationship();
        relationship.setRelationType(relationType);
        relationship.setDirection(direction);
        relationship.setDescription(description);
        relationship.setTags(tags);
        return relationship;
    }
}
