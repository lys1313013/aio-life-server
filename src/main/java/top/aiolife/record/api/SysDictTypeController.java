package top.aiolife.record.api;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.util.SysUtil;
import top.aiolife.record.convertor.SysDictDataConvertor;
import top.aiolife.record.mapper.ISysDictDataMapper;
import top.aiolife.record.mapper.ISysDictTypeMapper;
import top.aiolife.record.pojo.entity.SysDictDataEntity;
import top.aiolife.record.pojo.entity.SysDictTypeEntity;
import top.aiolife.record.pojo.vo.SysDictDataVO;
import top.aiolife.record.pojo.vo.SysDictTypeDetailVO;

import java.util.List;

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

    private ISysDictTypeMapper sysDictTypeMapper;
    private ISysDictDataMapper sysDictDataMapper;

    public ISysDictTypeMapper getBaseMapper() {
        return sysDictTypeMapper;
    }

    @GetMapping("/getByDictType")
    public ApiResponse<SysDictTypeDetailVO> queryById(String dictType) {
        // 根据类型英文名称查询类型id
        LambdaQueryWrapper<SysDictTypeEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysDictTypeEntity::getDictType, dictType);
        SysDictTypeEntity sysDictTypeEntity = sysDictTypeMapper.selectOne(queryWrapper);
        if (sysDictTypeEntity == null) {
            return ApiResponse.success();
        }

        LambdaQueryWrapper<SysDictDataEntity> typeQueryWrapper = new LambdaQueryWrapper<>();
        typeQueryWrapper.eq(SysDictDataEntity::getDictId, sysDictTypeEntity.getDictId());
        typeQueryWrapper.orderByAsc(SysDictDataEntity::getDictSort);
        List<SysDictDataEntity> sysDictDataEntityList = sysDictDataMapper.selectList(typeQueryWrapper);
        SysDictTypeDetailVO sysDictTypeDetailVO = new SysDictTypeDetailVO();
        sysDictTypeDetailVO.setSysDictTypeEntity(sysDictTypeEntity);

        List<SysDictDataVO> detailVoList = sysDictDataEntityList.stream().map(SysDictDataConvertor.INSTANCE::Entity2VO).toList();
        sysDictTypeDetailVO.setDictDetailList(detailVoList);

        return ApiResponse.success(sysDictTypeDetailVO);
    }

    @SaCheckRole("admin")
    @PostMapping("/query")
    public ApiResponse<PageResp<SysDictTypeEntity>> query(
            @RequestBody CommonQuery<SysDictTypeEntity> query) {
        LambdaQueryWrapper<SysDictTypeEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();

        SysDictTypeEntity condition = query.getCondition();
        if (condition != null) {
            lambdaQueryWrapper.like(SysUtil.isNotEmpty(condition.getDictName()),
                    SysDictTypeEntity::getDictName , condition.getDictName());
        }

        // 分页
        Page<SysDictTypeEntity> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<SysDictTypeEntity> iPage = getBaseMapper().selectPage(page, lambdaQueryWrapper);
        PageResp<SysDictTypeEntity> objectPageResp = PageResp.of(iPage.getRecords(), iPage.getTotal());
        return ApiResponse.success(objectPageResp);
    }

    @SaCheckRole("admin")
    @PostMapping
    public ApiResponse<Boolean> add(@RequestBody SysDictTypeEntity entity) {
        entity.setDictId(null);
        entity.setCreateBy(StpUtil.getLoginIdAsString());
        entity.setUpdateBy(StpUtil.getLoginIdAsString());
        return ApiResponse.success(getBaseMapper().insert(entity) > 0);
    }

    @SaCheckRole("admin")
    @PutMapping("/{dictId}")
    public ApiResponse<Boolean> update(@PathVariable("dictId") Long dictId, @RequestBody SysDictTypeEntity entity) {
        entity.setDictId(dictId);
        entity.setCreateBy(null);
        entity.setUpdateBy(StpUtil.getLoginIdAsString());
        return ApiResponse.success(getBaseMapper().updateById(entity) > 0);
    }

    @SaCheckRole("admin")
    @DeleteMapping("/{dictId}")
    public ApiResponse<Boolean> delete(@PathVariable("dictId") Long dictId) {
        SysDictTypeEntity entity = new SysDictTypeEntity();
        entity.setDictId(dictId);
        entity.setUpdateBy(StpUtil.getLoginIdAsString());
        boolean b = getBaseMapper().deleteById(entity) > 0;
        return ApiResponse.success(b);
    }
}
