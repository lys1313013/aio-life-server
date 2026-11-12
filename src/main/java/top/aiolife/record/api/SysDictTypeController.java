package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import java.time.LocalDateTime;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import top.aiolife.core.mybatis.IlikeQuery;
import top.aiolife.core.query.QueryOrder;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import top.aiolife.bankcard.service.BankCardDictionaryGuard;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.util.SysUtil;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.convertor.SysDictDataConvertor;
import top.aiolife.record.mapper.ISysDictDataMapper;
import top.aiolife.record.mapper.ISysDictTypeMapper;
import top.aiolife.record.pojo.entity.SysDictDataEntity;
import top.aiolife.record.pojo.entity.SysDictTypeEntity;
import top.aiolife.record.pojo.query.SysDictTypeFilterQuery;
import top.aiolife.record.pojo.req.SysDictTypeCreateReq;
import top.aiolife.record.pojo.req.SysDictTypeUpdateReq;
import top.aiolife.record.pojo.vo.SysDictDataVO;
import top.aiolife.record.pojo.vo.SysDictTypeDetailVO;
import top.aiolife.record.pojo.vo.SysDictTypeVO;

/**
 * 数据字典Controller
 *
 * @author Lys
 * @date 2025/04/05 22:17
 */
@RestController
@AllArgsConstructor
@RequestMapping("/sysDictType")
public class SysDictTypeController {
    private BankCardDictionaryGuard bankCardGuard;

    private ISysDictTypeMapper sysDictTypeMapper;
    private ISysDictDataMapper sysDictDataMapper;

    public ISysDictTypeMapper getBaseMapper() {
        return sysDictTypeMapper;
    }

    /**
     * 按字典类型标识查询系统字典类型及字典数据明细。
     */
    @Operation(summary = "查询系统字典类型及明细")
    @GetMapping("/getByDictType")
    public ApiResponse<SysDictTypeDetailVO> queryById(@Parameter(description = "系统字典类型标识") String dictType) {
        // 根据类型英文名称查询类型id
        LambdaQueryWrapper<SysDictTypeEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysDictTypeEntity::getDictType, dictType);
        SysDictTypeEntity sysDictTypeEntity = sysDictTypeMapper.selectOne(queryWrapper);
        if (sysDictTypeEntity == null) {
            return ApiResponse.success();
        }

        LambdaQueryWrapper<SysDictDataEntity> typeQueryWrapper = new LambdaQueryWrapper<>();
        typeQueryWrapper.eq(SysDictDataEntity::getDictId, sysDictTypeEntity.getDictId());
        QueryOrder.ascNullsFirst(typeQueryWrapper, SysDictDataEntity::getSortOrder);
        List<SysDictDataEntity> sysDictDataEntityList = sysDictDataMapper.selectList(typeQueryWrapper);
        SysDictTypeDetailVO sysDictTypeDetailVO = new SysDictTypeDetailVO();
        sysDictTypeDetailVO.setSysDictTypeEntity(RecordApiConvertor.INSTANCE.toSysDictTypeVO(sysDictTypeEntity));

        List<SysDictDataVO> detailVoList = sysDictDataEntityList.stream().map(SysDictDataConvertor.INSTANCE::Entity2VO).toList();
        sysDictTypeDetailVO.setDictDetailList(detailVoList);

        return ApiResponse.success(sysDictTypeDetailVO);
    }

    /**
     * 管理员分页查询系统字典类型。
     */
    @SaCheckRole("admin")
    @Operation(summary = "管理员分页查询系统字典类型")
    @GetMapping("/query")
    public ApiResponse<PageResp<SysDictTypeVO>> query(
            @QueryParams CommonQuery<SysDictTypeFilterQuery> requestQuery) {
        CommonQuery<SysDictTypeEntity> query = requestQuery.map(RecordApiConvertor.INSTANCE::fromSysDictTypeFilterQuery);
        LambdaQueryWrapper<SysDictTypeEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();

        SysDictTypeEntity condition = query.getCondition();
        if (condition != null) {
            IlikeQuery.anyOf(lambdaQueryWrapper, SysUtil.isNotEmpty(condition.getDictName()),
                    condition.getDictName(), SysDictTypeEntity::getDictName);
        }

        lambdaQueryWrapper.orderByAsc(SysDictTypeEntity::getDictId);
        // 分页
        Page<SysDictTypeEntity> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<SysDictTypeEntity> iPage = getBaseMapper().selectPage(page, lambdaQueryWrapper);
        PageResp<SysDictTypeEntity> objectPageResp = PageResp.of(iPage.getRecords(), iPage.getTotal());
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toSysDictTypeVOPage(objectPageResp));
    }

    /**
     * 管理员新增系统字典类型。
     */
    @SaCheckRole("admin")
    @Operation(summary = "管理员新增系统字典类型")
    @PostMapping
    public ApiResponse<Boolean> add(@Valid @RequestBody SysDictTypeCreateReq entityReq) {
        SysDictTypeEntity entity = RecordApiConvertor.INSTANCE.fromSysDictTypeCreateReq(entityReq);
        entity.setDictId(null);
        entity.setCreateUser(StpUtil.getLoginIdAsLong());
        entity.setUpdateUser(StpUtil.getLoginIdAsLong());
        try {
            return ApiResponse.success(getBaseMapper().insert(entity) > 0);
        } catch (DuplicateKeyException e) {
            return ApiResponse.error("字典类型已存在");
        }
    }

    /**
     * 管理员修改系统字典类型。
     */
    @SaCheckRole("admin")
    @Transactional
    @Operation(summary = "管理员更新系统字典类型")
    @PutMapping("/{dictId}")
    public ApiResponse<Boolean> update(@PathVariable("dictId") Long dictId, @Valid @RequestBody SysDictTypeUpdateReq entityReq) {
        SysDictTypeEntity entity = RecordApiConvertor.INSTANCE.fromSysDictTypeUpdateReq(entityReq);
        bankCardGuard.checkTypeChange(dictId, entity.getDictType(), Integer.valueOf(1).equals(entity.getIsDeleted()));
        entity.setDictId(dictId);
        entity.setCreateUser(null);
        entity.setUpdateUser(StpUtil.getLoginIdAsLong());
        entity.setUpdateTime(LocalDateTime.now());
        try {
            return ApiResponse.success(getBaseMapper().updateById(entity) > 0);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("字典类型已存在");
        }
    }

    /**
     * 管理员删除系统字典类型。
     */
    @SaCheckRole("admin")
    @Transactional
    @Operation(summary = "管理员删除系统字典类型")
    @DeleteMapping("/{dictId}")
    public ApiResponse<Boolean> delete(@PathVariable("dictId") Long dictId) {
        bankCardGuard.checkTypeChange(dictId, null, true);
        SysDictTypeEntity entity = new SysDictTypeEntity();
        entity.setDictId(dictId);
        entity.setUpdateUser(StpUtil.getLoginIdAsLong());
        boolean b = getBaseMapper().deleteById(entity) > 0;
        return ApiResponse.success(b);
    }
}
