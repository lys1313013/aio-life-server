package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import top.aiolife.core.mybatis.IlikeQuery;
import top.aiolife.core.query.QueryOrder;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.bankcard.service.BankCardDictionaryGuard;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.util.SysUtil;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.mapper.ISysDictDataMapper;
import top.aiolife.record.mapper.ISysDictTypeMapper;
import top.aiolife.record.pojo.entity.SysDictDataEntity;
import top.aiolife.record.pojo.entity.SysDictTypeEntity;
import top.aiolife.record.pojo.query.SysDictTypeQuery;
import top.aiolife.record.pojo.req.SysDictDataCreateReq;
import top.aiolife.record.pojo.req.SysDictDataUpdateReq;
import top.aiolife.record.pojo.vo.SysDictDataRecordVO;

/**
 * 字典数据Controller
 *
 * @author Lys
 * @date 2025/04/06 17:24
 */
@RestController
@AllArgsConstructor
@RequestMapping("/sysDictData")
public class SysDictDataController {
    private BankCardDictionaryGuard bankCardGuard;
    private ISysDictDataMapper sysDictDataMapper;
    private ISysDictTypeMapper sysDictTypeMapper;

    public ISysDictDataMapper getBaseMapper() {
        return sysDictDataMapper;
    }

    /**
     * 管理员分页查询系统字典数据，支持按字典类型和标签筛选。
     */
    @SaCheckRole("admin")
    @Operation(summary = "管理员分页查询系统字典数据")
    @GetMapping("/query")
    public ApiResponse<PageResp<SysDictDataRecordVO>> query(
            @QueryParams CommonQuery<SysDictTypeQuery> query) {
        LambdaQueryWrapper<SysDictDataEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        QueryOrder.ascNullsFirst(lambdaQueryWrapper, SysDictDataEntity::getDictId, SysDictDataEntity::getSortOrder, SysDictDataEntity::getDictCode);

        SysDictTypeQuery condition = query.getCondition();
        if (condition != null && SysUtil.isNotEmpty(condition.getDictLabel())) {
            IlikeQuery.anyOf(lambdaQueryWrapper, condition.getDictLabel(), SysDictDataEntity::getDictLabel);
        }

        // 字典名称使用字典类型表过滤查询
        if (condition != null && SysUtil.isNotEmpty(condition.getDictType())) {
            LambdaQueryWrapper<SysDictTypeEntity> typeQueryWrapper = new LambdaQueryWrapper<>();
            typeQueryWrapper.eq(SysDictTypeEntity::getDictType, condition.getDictType());
            List<SysDictTypeEntity> sysDictTypeEntities = sysDictTypeMapper.selectList(typeQueryWrapper);
            if (SysUtil.isEmpty(sysDictTypeEntities)) {
                return ApiResponse.success();
            } else {
                Collection<Long> dictIdList = sysDictTypeEntities.stream().map(SysDictTypeEntity::getDictId).distinct().toList();
                lambdaQueryWrapper.in(SysDictDataEntity::getDictId, dictIdList);
            }
        }

        // 分页
        Page<SysDictDataEntity> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<SysDictDataEntity> iPage = getBaseMapper().selectPage(page, lambdaQueryWrapper);
        if (iPage.getTotal() == 0) {
            return ApiResponse.success(RecordApiConvertor.INSTANCE.toSysDictDataRecordVOPage(PageResp.of()));
        }

        List<SysDictDataEntity> records = iPage.getRecords();
        if (records.isEmpty()) {
            return ApiResponse.success(RecordApiConvertor.INSTANCE.toSysDictDataRecordVOPage(
                    PageResp.of(records, iPage.getTotal())));
        }

        // 补充dictName
        Collection<Long> dictIdList = records.stream().map(SysDictDataEntity::getDictId).distinct().toList();
        List<SysDictTypeEntity> sysDictTypeEntities = sysDictTypeMapper.selectByIds(dictIdList);
        records.forEach(sysDictDataEntity -> {
            for (SysDictTypeEntity sysDictTypeEntity : sysDictTypeEntities)
                if (sysDictDataEntity.getDictId().equals(sysDictTypeEntity.getDictId())) {
                    sysDictDataEntity.setDictName(sysDictTypeEntity.getDictName());
                    sysDictDataEntity.setDictType(sysDictTypeEntity.getDictType());
                    break;
                }
        });
        PageResp<SysDictDataEntity> objectPageResp = PageResp.of(records, iPage.getTotal());
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toSysDictDataRecordVOPage(objectPageResp));
    }

    /**
     * 管理员新增系统字典数据。
     */
    @SaCheckRole("admin")
    @Operation(summary = "管理员新增系统字典数据")
    @PostMapping
    public ApiResponse<Boolean> add(@Valid @RequestBody SysDictDataCreateReq entityReq) {
        SysDictDataEntity entity = RecordApiConvertor.INSTANCE.fromSysDictDataCreateReq(entityReq);
        entity.setDictCode(null);
        entity.setCreateUser(StpUtil.getLoginIdAsLong());
        entity.setUpdateUser(StpUtil.getLoginIdAsLong());
        try {
            return ApiResponse.success(getBaseMapper().insert(entity) > 0);
        } catch (DuplicateKeyException e) {
            return ApiResponse.error("该字典类型下字典值已存在");
        }
    }

    /**
     * 管理员修改系统字典数据。
     */
    @SaCheckRole("admin")
    @Transactional
    @Operation(summary = "管理员更新系统字典数据")
    @PutMapping("/{dictCode}")
    public ApiResponse<Boolean> update(@PathVariable("dictCode") Long dictCode, @Valid @RequestBody SysDictDataUpdateReq entityReq) {
        SysDictDataEntity entity = RecordApiConvertor.INSTANCE.fromSysDictDataUpdateReq(entityReq);
        bankCardGuard.checkBankChange(dictCode, entity.getDictId(), Integer.valueOf(1).equals(entity.getIsDeleted()));
        entity.setDictCode(dictCode);
        entity.setCreateUser(null);
        entity.setUpdateUser(StpUtil.getLoginIdAsLong());
        try {
            return ApiResponse.success(getBaseMapper().updateById(entity) > 0);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("该字典类型下字典值已存在");
        }
    }

    /**
     * 管理员删除系统字典数据。
     */
    @SaCheckRole("admin")
    @Transactional
    @Operation(summary = "管理员删除系统字典数据")
    @DeleteMapping("/{dictCode}")
    public ApiResponse<Boolean> delete(@PathVariable("dictCode") Long dictCode) {
        bankCardGuard.checkBankChange(dictCode, null, true);
        boolean b = getBaseMapper().deleteById(dictCode) > 0;
        return ApiResponse.success(b);
    }
}
