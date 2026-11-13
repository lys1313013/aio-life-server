package top.aiolife.record.api;

import cn.dev33.satoken.annotation.SaCheckRole;
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
import top.aiolife.record.pojo.entity.SysDictTypeEntity;
import top.aiolife.record.pojo.query.SysDictTypeFilterQuery;
import top.aiolife.record.pojo.req.SysDictTypeCreateReq;
import top.aiolife.record.pojo.req.SysDictTypeUpdateReq;
import top.aiolife.record.pojo.vo.SysDictTypeVO;
import top.aiolife.record.service.ISysDictService;

/**
 * 系统字典类型管理端控制器。
 *
 * @author Lys
 * @date 2025/04/05 22:17
 */
@Tag(name = "系统字典类型管理")
@SaCheckRole("admin")
@RestController
@AllArgsConstructor
@RequestMapping("/sysDictType")
public class SysDictTypeAdminController {

    private final ISysDictService sysDictService;

    @Operation(summary = "管理员分页查询系统字典类型")
    @GetMapping("/query")
    public ApiResponse<PageResp<SysDictTypeVO>> query(
            @QueryParams CommonQuery<SysDictTypeFilterQuery> requestQuery) {
        CommonQuery<SysDictTypeEntity> query = requestQuery.map(
                RecordApiConvertor.INSTANCE::fromSysDictTypeFilterQuery);
        SysDictTypeEntity condition = query.getCondition();
        PageResp<SysDictTypeEntity> page = sysDictService.pageSysDictType(
                condition == null ? null : condition.getDictName(),
                query.getPage(), query.getPageSize());
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toSysDictTypeVOPage(page));
    }

    @Operation(summary = "管理员新增系统字典类型")
    @PostMapping
    public ApiResponse<Boolean> add(@CurrentUserId String userId,
                                    @Valid @RequestBody SysDictTypeCreateReq entityReq) {
        SysDictTypeEntity entity = RecordApiConvertor.INSTANCE.fromSysDictTypeCreateReq(entityReq);
        return ApiResponse.success(sysDictService.createSysDictType(entity, IdCodec.decode(userId)));
    }

    @Operation(summary = "管理员更新系统字典类型")
    @PutMapping("/{dictId}")
    public ApiResponse<Boolean> update(@CurrentUserId String userId,
                                       @PathVariable("dictId") String dictId,
                                       @Valid @RequestBody SysDictTypeUpdateReq entityReq) {
        SysDictTypeEntity entity = RecordApiConvertor.INSTANCE.fromSysDictTypeUpdateReq(entityReq);
        return ApiResponse.success(sysDictService.updateSysDictType(
                IdCodec.decode(dictId), entity, IdCodec.decode(userId)));
    }

    @Operation(summary = "管理员删除系统字典类型")
    @DeleteMapping("/{dictId}")
    public ApiResponse<Boolean> delete(@CurrentUserId String userId,
                                       @PathVariable("dictId") String dictId) {
        return ApiResponse.success(sysDictService.deleteSysDictType(
                IdCodec.decode(dictId), IdCodec.decode(userId)));
    }
}
