package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.pojo.query.UserDictDataQuery;
import top.aiolife.record.pojo.req.UserDictDataCreateReq;
import top.aiolife.record.pojo.req.UserDictDataUpdateReq;
import top.aiolife.record.pojo.vo.UserDictDataVO;
import top.aiolife.record.service.UserDictDataService;

/**
 * 用户字典数据控制器。
 *
 * @author Lys
 */
@Tag(name = "用户字典数据")
@RestController
@AllArgsConstructor
@RequestMapping("/userDictData")
public class UserDictDataController {

    private final UserDictDataService userDictDataService;

    /**
     * 分页查询当前用户可见的字典数据，包含公共模板、个人配置和已停用项。
     */
    @Operation(summary = "分页查询可见用户字典")
    @GetMapping("/query")
    public ApiResponse<PageResp<UserDictDataVO>> query(@CurrentUserId String userId,
                                                       @QueryParams CommonQuery<UserDictDataQuery> requestQuery) {
        CommonQuery<UserDictDataEntity> query = requestQuery.map(
                RecordApiConvertor.INSTANCE::fromUserDictDataQuery);
        UserDictDataEntity condition = query.getCondition();
        PageResp<UserDictDataEntity> page = userDictDataService.pageVisibleDictData(
                IdCodec.decode(userId),
                condition == null ? null : condition.getDictType(),
                condition == null ? null : condition.getDictLabel(),
                condition == null ? null : condition.getIsEnabled(),
                query.getPage(),
                query.getPageSize());
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toUserDictDataVOPage(page));
    }

    /**
     * 按 ID 查询当前用户或公共模板的字典数据，用于解析业务关联 ID 对应的分类名称等信息。
     * 返回原始记录，不合并个人覆盖配置；需要当前用户的展示配置时按字典类型查询可见数据。
     */
    @Operation(summary = "按 ID 查询原始用户字典数据")
    @GetMapping("/{id}")
    public ApiResponse<UserDictDataVO> getById(@CurrentUserId String userId,
                                               @PathVariable("id") String id) {
        UserDictDataEntity entity = userDictDataService.getAccessibleById(
                IdCodec.decode(userId), IdCodec.decode(id));
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toUserDictDataVO(entity));
    }

    /**
     * 新增当前用户的字典数据。
     */
    @Operation(summary = "新增用户字典数据")
    @PostMapping
    public ApiResponse<Boolean> insert(@CurrentUserId String userId,
                                       @Valid @RequestBody UserDictDataCreateReq entityReq) {
        UserDictDataEntity entity = RecordApiConvertor.INSTANCE.fromUserDictDataCreateReq(entityReq);
        userDictDataService.createDictData(entity, IdCodec.decode(userId));
        return ApiResponse.success(true);
    }

    /**
     * 修改当前用户的字典数据或个人覆盖配置。
     */
    @Operation(summary = "更新用户字典配置")
    @PutMapping
    public ApiResponse<Boolean> update(@CurrentUserId String userId,
                                       @Valid @RequestBody UserDictDataUpdateReq entityReq) {
        UserDictDataEntity entity = RecordApiConvertor.INSTANCE.fromUserDictDataUpdateReq(entityReq);
        userDictDataService.updateDictData(entity.getId(), entity, IdCodec.decode(userId));
        return ApiResponse.success(true);
    }

    /**
     * 删除当前用户的字典数据；公共字典仅对当前用户隐藏。
     */
    @Operation(summary = "删除或隐藏用户字典数据")
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@CurrentUserId String userId,
                                       @PathVariable("id") String id) {
        userDictDataService.deleteDictData(IdCodec.decode(id), IdCodec.decode(userId));
        return ApiResponse.success(true);
    }
}
