package top.aiolife.record.api;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import java.util.Collection;
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
    private top.aiolife.bankcard.service.BankCardDictionaryGuard bankCardGuard;
    private ISysDictDataMapper sysDictDataMapper;
    private ISysDictTypeMapper sysDictTypeMapper;

    public ISysDictDataMapper getBaseMapper() {
        return sysDictDataMapper;
    }

    @SaCheckRole("admin")
    @GetMapping("/query")
    public ApiResponse<PageResp<SysDictDataRecordVO>> query(
            @QueryParams CommonQuery<SysDictTypeQuery> query) {
        LambdaQueryWrapper<SysDictDataEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.orderByAsc(SysDictDataEntity::getDictId, SysDictDataEntity::getDictSort);

        SysDictTypeQuery condition = query.getCondition();
        if (condition != null && SysUtil.isNotEmpty(condition.getDictLabel())) {
            lambdaQueryWrapper.like(SysDictDataEntity::getDictLabel, condition.getDictLabel());
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

    @SaCheckRole("admin")
    @PostMapping
    public ApiResponse<Boolean> add(@Valid @RequestBody SysDictDataCreateReq entityReq) {
        SysDictDataEntity entity = RecordApiConvertor.INSTANCE.fromSysDictDataCreateReq(entityReq);
        entity.setDictCode(null);
        entity.setCreateUser(StpUtil.getLoginIdAsLong());
        entity.setUpdateUser(StpUtil.getLoginIdAsLong());
        return ApiResponse.success(getBaseMapper().insert(entity) > 0);
    }

    @SaCheckRole("admin")
    @org.springframework.transaction.annotation.Transactional
    @PutMapping("/{dictCode}")
    public ApiResponse<Boolean> update(@PathVariable("dictCode") Long dictCode, @Valid @RequestBody SysDictDataUpdateReq entityReq) {
        SysDictDataEntity entity = RecordApiConvertor.INSTANCE.fromSysDictDataUpdateReq(entityReq);
        bankCardGuard.checkBankChange(dictCode, entity.getDictId(), Integer.valueOf(1).equals(entity.getIsDeleted()));
        entity.setDictCode(dictCode);
        entity.setCreateUser(null);
        entity.setUpdateUser(StpUtil.getLoginIdAsLong());
        return ApiResponse.success(getBaseMapper().updateById(entity) > 0);
    }

    @SaCheckRole("admin")
    @org.springframework.transaction.annotation.Transactional
    @DeleteMapping("/{dictCode}")
    public ApiResponse<Boolean> delete(@PathVariable("dictCode") Long dictCode) {
        bankCardGuard.checkBankChange(dictCode, null, true);
        boolean b = getBaseMapper().deleteById(dictCode) > 0;
        return ApiResponse.success(b);
    }
}
