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
import top.aiolife.record.pojo.entity.SysDictDataEntity;
import top.aiolife.record.pojo.query.SysDictTypeQuery;
import top.aiolife.record.pojo.req.SysDictDataCreateReq;
import top.aiolife.record.pojo.req.SysDictDataUpdateReq;
import top.aiolife.record.pojo.vo.SysDictDataRecordVO;
import top.aiolife.record.service.ISysDictService;

/**
 * 系统字典数据管理端控制器。
 *
 * @author Lys
 * @date 2025/04/06 17:24
 */
@Tag(name = "系统字典数据")
@SaCheckRole("admin")
@RestController
@AllArgsConstructor
@RequestMapping("/sysDictData")
public class SysDictDataAdminController {

    private final ISysDictService sysDictService;

    @Operation(summary = "管理员分页查询系统字典数据")
    @GetMapping("/query")
    public ApiResponse<PageResp<SysDictDataRecordVO>> query(
            @QueryParams CommonQuery<SysDictTypeQuery> query) {
        PageResp<SysDictDataEntity> page = sysDictService.pageSysDictData(
                query.getCondition(), query.getPage(), query.getPageSize());
        if (page == null) {
            return ApiResponse.success();
        }
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toSysDictDataRecordVOPage(page));
    }

    @Operation(summary = "管理员新增系统字典数据")
    @PostMapping
    public ApiResponse<Boolean> add(@CurrentUserId String userId,
                                    @Valid @RequestBody SysDictDataCreateReq entityReq) {
        SysDictDataEntity entity = RecordApiConvertor.INSTANCE.fromSysDictDataCreateReq(entityReq);
        return ApiResponse.success(sysDictService.createSysDictData(entity, IdCodec.decode(userId)));
    }

    @Operation(summary = "管理员更新系统字典数据")
    @PutMapping("/{dictCode}")
    public ApiResponse<Boolean> update(@CurrentUserId String userId,
                                       @PathVariable("dictCode") String dictCode,
                                       @Valid @RequestBody SysDictDataUpdateReq entityReq) {
        SysDictDataEntity entity = RecordApiConvertor.INSTANCE.fromSysDictDataUpdateReq(entityReq);
        return ApiResponse.success(sysDictService.updateSysDictData(
                IdCodec.decode(dictCode), entity, IdCodec.decode(userId)));
    }

    @Operation(summary = "管理员删除系统字典数据")
    @DeleteMapping("/{dictCode}")
    public ApiResponse<Boolean> delete(@CurrentUserId String userId,
                                       @PathVariable("dictCode") String dictCode) {
        return ApiResponse.success(sysDictService.deleteSysDictData(
                IdCodec.decode(dictCode), IdCodec.decode(userId)));
    }
}
