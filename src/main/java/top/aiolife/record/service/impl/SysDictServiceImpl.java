package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.bankcard.service.BankCardDictionaryGuard;
import top.aiolife.core.mybatis.IlikeQuery;
import top.aiolife.core.query.QueryOrder;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.util.SysUtil;
import top.aiolife.record.mapper.ISysDictDataMapper;
import top.aiolife.record.mapper.ISysDictTypeMapper;
import top.aiolife.record.pojo.entity.SysDictDataEntity;
import top.aiolife.record.pojo.entity.SysDictTypeEntity;
import top.aiolife.record.pojo.query.SysDictTypeQuery;
import top.aiolife.record.service.ISysDictService;

/**
 * 系统字典服务实现。
 *
 * @author Lys
 * @date 2025/10/03 14:25
 */
@Service
@AllArgsConstructor
public class SysDictServiceImpl implements ISysDictService {

    private final ISysDictDataMapper sysDictDataMapper;

    private final ISysDictTypeMapper sysDictTypeMapper;

    private final BankCardDictionaryGuard bankCardGuard;

    @Override
    public List<SysDictDataEntity> getDictDataByDictType(String dictType) {
        LambdaQueryWrapper<SysDictTypeEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysDictTypeEntity::getDictType, dictType);
        SysDictTypeEntity sysDictTypeEntity = sysDictTypeMapper.selectOne(queryWrapper);
        if (sysDictTypeEntity == null) {
            return List.of();
        }
        LambdaQueryWrapper<SysDictDataEntity> typeQueryWrapper = new LambdaQueryWrapper<>();
        typeQueryWrapper.eq(SysDictDataEntity::getDictTypeId, sysDictTypeEntity.getId());
        QueryOrder.ascNullsFirst(typeQueryWrapper, SysDictDataEntity::getSortOrder);
        return sysDictDataMapper.selectList(typeQueryWrapper);
    }

    @Override
    public DictTypeDetail getDictTypeDetail(String dictType) {
        LambdaQueryWrapper<SysDictTypeEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysDictTypeEntity::getDictType, dictType);
        SysDictTypeEntity sysDictTypeEntity = sysDictTypeMapper.selectOne(queryWrapper);
        if (sysDictTypeEntity == null) {
            return null;
        }
        LambdaQueryWrapper<SysDictDataEntity> typeQueryWrapper = new LambdaQueryWrapper<>();
        typeQueryWrapper.eq(SysDictDataEntity::getDictTypeId, sysDictTypeEntity.getId());
        QueryOrder.ascNullsFirst(typeQueryWrapper, SysDictDataEntity::getSortOrder);
        return new DictTypeDetail(sysDictTypeEntity, sysDictDataMapper.selectList(typeQueryWrapper));
    }

    @Override
    public PageResp<SysDictDataEntity> pageSysDictData(SysDictTypeQuery condition, int page, int pageSize) {
        LambdaQueryWrapper<SysDictDataEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        QueryOrder.ascNullsFirst(lambdaQueryWrapper, SysDictDataEntity::getDictTypeId,
                SysDictDataEntity::getSortOrder, SysDictDataEntity::getId);

        if (condition != null && SysUtil.isNotEmpty(condition.getDictLabel())) {
            IlikeQuery.anyOf(lambdaQueryWrapper, condition.getDictLabel(), SysDictDataEntity::getDictLabel);
        }

        // 字典名称使用字典类型表过滤查询
        if (condition != null && SysUtil.isNotEmpty(condition.getDictType())) {
            LambdaQueryWrapper<SysDictTypeEntity> typeQueryWrapper = new LambdaQueryWrapper<>();
            typeQueryWrapper.eq(SysDictTypeEntity::getDictType, condition.getDictType());
            List<SysDictTypeEntity> sysDictTypeEntities = sysDictTypeMapper.selectList(typeQueryWrapper);
            if (SysUtil.isEmpty(sysDictTypeEntities)) {
                // 类型不存在时保持原接口返回空数据
                return null;
            }
            Collection<Long> dictTypeIdList = sysDictTypeEntities.stream()
                    .map(SysDictTypeEntity::getId).distinct().toList();
            lambdaQueryWrapper.in(SysDictDataEntity::getDictTypeId, dictTypeIdList);
        }

        Page<SysDictDataEntity> pageRequest = new Page<>(page, pageSize);
        IPage<SysDictDataEntity> iPage = sysDictDataMapper.selectPage(pageRequest, lambdaQueryWrapper);
        if (iPage.getTotal() == 0) {
            return PageResp.of();
        }

        List<SysDictDataEntity> records = iPage.getRecords();
        if (records.isEmpty()) {
            return PageResp.of(records, iPage.getTotal());
        }

        // 补充 dictName
        Collection<Long> dictTypeIdList = records.stream()
                .map(SysDictDataEntity::getDictTypeId).distinct().toList();
        List<SysDictTypeEntity> sysDictTypeEntities = sysDictTypeMapper.selectByIds(dictTypeIdList);
        records.forEach(sysDictDataEntity -> {
            for (SysDictTypeEntity sysDictTypeEntity : sysDictTypeEntities) {
                if (sysDictDataEntity.getDictTypeId().equals(sysDictTypeEntity.getId())) {
                    sysDictDataEntity.setDictName(sysDictTypeEntity.getDictName());
                    sysDictDataEntity.setDictType(sysDictTypeEntity.getDictType());
                    break;
                }
            }
        });
        return PageResp.of(records, iPage.getTotal());
    }

    @Override
    @Transactional
    public boolean createSysDictData(SysDictDataEntity entity, long userId) {
        entity.setId(null);
        entity.setCreateUser(userId);
        entity.setUpdateUser(userId);
        try {
            return sysDictDataMapper.insert(entity) > 0;
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("该字典类型下字典值已存在");
        }
    }

    @Override
    @Transactional
    public boolean updateSysDictData(long id, SysDictDataEntity entity, long userId) {
        bankCardGuard.checkBankChange(id, entity.getDictTypeId(), Integer.valueOf(1).equals(entity.getIsDeleted()));
        entity.setId(id);
        entity.setCreateUser(null);
        entity.setUpdateUser(userId);
        try {
            return sysDictDataMapper.updateById(entity) > 0;
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("该字典类型下字典值已存在");
        }
    }

    @Override
    @Transactional
    public boolean deleteSysDictData(long id, long userId) {
        bankCardGuard.checkBankChange(id, null, true);
        return sysDictDataMapper.deleteById(id) > 0;
    }

    @Override
    public PageResp<SysDictTypeEntity> pageSysDictType(String dictName, int page, int pageSize) {
        LambdaQueryWrapper<SysDictTypeEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        IlikeQuery.anyOf(lambdaQueryWrapper, SysUtil.isNotEmpty(dictName),
                dictName, SysDictTypeEntity::getDictName);
        lambdaQueryWrapper.orderByAsc(SysDictTypeEntity::getId);

        Page<SysDictTypeEntity> pageRequest = new Page<>(page, pageSize);
        IPage<SysDictTypeEntity> iPage = sysDictTypeMapper.selectPage(pageRequest, lambdaQueryWrapper);
        return PageResp.of(iPage.getRecords(), iPage.getTotal());
    }

    @Override
    @Transactional
    public boolean createSysDictType(SysDictTypeEntity entity, long userId) {
        entity.setId(null);
        entity.setCreateUser(userId);
        entity.setUpdateUser(userId);
        try {
            return sysDictTypeMapper.insert(entity) > 0;
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("字典类型已存在");
        }
    }

    @Override
    @Transactional
    public boolean updateSysDictType(long id, SysDictTypeEntity entity, long userId) {
        bankCardGuard.checkTypeChange(id, entity.getDictType(), Integer.valueOf(1).equals(entity.getIsDeleted()));
        entity.setId(id);
        entity.setCreateUser(null);
        entity.setUpdateUser(userId);
        entity.setUpdateTime(LocalDateTime.now());
        try {
            return sysDictTypeMapper.updateById(entity) > 0;
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("字典类型已存在");
        }
    }

    @Override
    @Transactional
    public boolean deleteSysDictType(long id, long userId) {
        bankCardGuard.checkTypeChange(id, null, true);
        SysDictTypeEntity entity = new SysDictTypeEntity();
        entity.setId(id);
        entity.setUpdateUser(userId);
        return sysDictTypeMapper.deleteById(entity) > 0;
    }
}
