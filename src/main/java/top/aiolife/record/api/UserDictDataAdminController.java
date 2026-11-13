package top.aiolife.record.api;

import cn.dev33.satoken.annotation.SaCheckRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.pojo.query.UserDictDataQuery;
import top.aiolife.record.pojo.req.UserDictDataAdminCreateReq;
import top.aiolife.record.pojo.req.UserDictDataAdminUpdateReq;
import top.aiolife.record.pojo.req.UserDictDataReSortReq;
import top.aiolife.record.pojo.vo.UserDictDataSortVO;
import top.aiolife.record.pojo.vo.UserDictDataVO;
import top.aiolife.record.service.UserDictDataService;

/**
 * 用户字典数据管理端控制器。
 *
 * @author Lys
 */
@Tag(name = "用户字典数据管理")
@SaCheckRole("admin")
@RestController
@AllArgsConstructor
@RequestMapping("/userDictData/admin")
public class UserDictDataAdminController {

    private final UserDictDataService userDictDataService;

    /**
     * 管理员分页查询公共基础字典数据。
     */
    @Operation(summary = "管理员分页查询公共字典数据")
    @GetMapping("/query")
    public ApiResponse<PageResp<UserDictDataVO>> adminQuery(
            @QueryParams CommonQuery<UserDictDataQuery> requestQuery) {
        CommonQuery<UserDictDataEntity> query = requestQuery.map(
                RecordApiConvertor.INSTANCE::fromUserDictDataQuery);
        UserDictDataEntity condition = query.getCondition();
        PageResp<UserDictDataEntity> page = userDictDataService.pageBaseDictData(
                condition == null ? null : condition.getDictType(),
                condition == null ? null : condition.getDictLabel(),
                condition == null ? null : condition.getIsEnabled(),
                query.getPage(),
                query.getPageSize());
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toUserDictDataVOPage(page));
    }

    /**
     * 管理员新增公共基础字典数据。
     */
    @Operation(summary = "管理员新增公共字典数据")
    @PostMapping
    public ApiResponse<Boolean> adminInsert(
            @Valid @RequestBody UserDictDataAdminCreateReq entityReq) {
        UserDictDataEntity entity = RecordApiConvertor.INSTANCE.fromUserDictDataAdminCreateReq(entityReq);
        return ApiResponse.success(userDictDataService.createBaseDictData(entity));
    }

    /**
     * 管理员修改公共基础字典数据。
     */
    @Operation(summary = "管理员更新公共字典数据")
    @PutMapping("/{id}")
    public ApiResponse<Boolean> adminUpdate(@PathVariable("id") String id,
                                            @Valid @RequestBody UserDictDataAdminUpdateReq entityReq) {
        UserDictDataEntity entity = RecordApiConvertor.INSTANCE.fromUserDictDataAdminUpdateReq(entityReq);
        return ApiResponse.success(userDictDataService.updateBaseDictData(IdCodec.decode(id), entity));
    }

    /**
     * 管理员调整同类型公共基础字典数据的排序。
     */
    @Operation(summary = "管理员调整公共字典排序")
    @PostMapping("/reSort")
    public ApiResponse<List<UserDictDataSortVO>> adminReSort(
            @Valid @RequestBody UserDictDataReSortReq req) {
        List<UserDictDataSortVO> result = userDictDataService.reSortBaseDictData(
                req.getDictType(),
                req.getDragId(),
                req.getTargetId(),
                req.getPosition());
        return ApiResponse.success(result);
    }

    /**
     * 管理员删除公共基础字典数据。
     */
    @Operation(summary = "管理员删除公共字典数据")
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> adminDelete(@PathVariable("id") String id) {
        return ApiResponse.success(userDictDataService.deleteBaseDictData(IdCodec.decode(id)));
    }
}
