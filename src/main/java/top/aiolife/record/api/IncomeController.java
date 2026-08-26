package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.util.SysUtil;
import top.aiolife.record.mapper.IIncomeMapper;
import top.aiolife.record.pojo.entity.IncomeEntity;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.pojo.query.IncomeQuery;
import top.aiolife.record.pojo.vo.IncStaByYearVO;
import top.aiolife.record.pojo.vo.IncStaticByYearVO;
import top.aiolife.record.service.UserDictDataService;
import lombok.AllArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/09/14 21:09
 */
@RestController
@AllArgsConstructor
@RequestMapping("/income")
public class IncomeController {

    private IIncomeMapper incomeMapper;

    private UserDictDataService userDictDataService;

    public IIncomeMapper getBaseMapper() {
        return incomeMapper;
    }


    @PostMapping("/query")
    public ApiResponse<PageResp<IncomeEntity>> query(
            @RequestBody CommonQuery<IncomeQuery> query) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<IncomeEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(IncomeEntity::getUserId, userId);
        IncomeQuery condition = query.getCondition();
        lambdaQueryWrapper.eq(SysUtil.isNotEmpty(condition.getIncTypeId()), IncomeEntity::getIncTypeId,
                condition.getIncTypeId());
        lambdaQueryWrapper.likeRight(SysUtil.isNotEmpty(condition.getYear()), IncomeEntity::getIncDate, condition.getYear());
        lambdaQueryWrapper.ge(SysUtil.isNotEmpty(condition.getStartTime()), IncomeEntity::getIncDate, condition.getStartTime());
        lambdaQueryWrapper.le(SysUtil.isNotEmpty(condition.getEndTime()), IncomeEntity::getIncDate, condition.getEndTime());
        lambdaQueryWrapper.orderByDesc(IncomeEntity::getIncDate);
        Page<IncomeEntity> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<IncomeEntity> iPage = incomeMapper.selectPage(page, lambdaQueryWrapper);
        PageResp<IncomeEntity> objectPageResp = PageResp.of(iPage.getRecords(), iPage.getTotal());
        return ApiResponse.success(objectPageResp);
    }


    @PostMapping
    public ApiResponse<Boolean> add(@Validated @RequestBody IncomeEntity entity) {
        entity.setId(null);
        entity.setUserId(StpUtil.getLoginIdAsLong());
        return ApiResponse.success(getBaseMapper().insert(entity) > 0);
    }

    @PutMapping("/{incomeId}")
    public ApiResponse<Boolean> update(@PathVariable("incomeId") Long incomeId, @Validated @RequestBody IncomeEntity entity) {
        Long userId = StpUtil.getLoginIdAsLong();
        entity.setId(incomeId);
        entity.setUserId(null);
        LambdaQueryWrapper<IncomeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IncomeEntity::getId, incomeId);
        wrapper.eq(IncomeEntity::getUserId, userId);
        return ApiResponse.success(getBaseMapper().update(entity, wrapper) > 0);
    }

    @DeleteMapping("/{incomeId}")
    public ApiResponse<Boolean> delete(@PathVariable("incomeId") Long incomeId) {
        LambdaQueryWrapper<IncomeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IncomeEntity::getId, incomeId);
        wrapper.eq(IncomeEntity::getUserId, StpUtil.getLoginIdAsLong());
        boolean b = getBaseMapper().delete(wrapper) > 0;
        return ApiResponse.success(b);
    }

    @PostMapping("/statisticsByYear")
    public ApiResponse<Object> statisticsByYear() {
        long userId = StpUtil.getLoginIdAsLong();
        List<IncStaByYearVO> list = incomeMapper.statisticsByYear(userId);
        List<IncStaticByYearVO> ans = new ArrayList<>();
        
        // 获取收入类型字典数据
        List<UserDictDataEntity> dictDataList = userDictDataService.listUserVisibleDictData(userId, "income_type");
        Map<Long, String> dictMap = dictDataList.stream()
                .collect(Collectors.toMap(UserDictDataEntity::getId, UserDictDataEntity::getDictLabel));

        // 按照年度汇总
        Map<Integer, List<IncStaByYearVO>> collect = list.stream()
                .collect(Collectors.groupingBy(IncStaByYearVO::getYear));
        for (Map.Entry<Integer, List<IncStaByYearVO>> entry : collect.entrySet()) {
            IncStaticByYearVO incStaticByYearVO = new IncStaticByYearVO();
            incStaticByYearVO.setYear(entry.getKey());
            List<IncStaByYearVO> value = entry.getValue();
            value.forEach(item -> {
                item.setTypeName(dictMap.get(item.getTypeId()));
            });
            incStaticByYearVO.setDetail(value);
            ans.add(incStaticByYearVO);
        }
        return ApiResponse.success(ans);
    }
    
    @PostMapping("/statisticsByMonth")
    public ApiResponse<Object> statisticsByMonth() {
        long userId = StpUtil.getLoginIdAsLong();
        List<IncStaByYearVO> list = incomeMapper.statisticsByMonth(userId);
        List<IncStaticByYearVO> ans = new ArrayList<>();

        // 获取收入类型字典数据
        List<UserDictDataEntity> dictDataList = userDictDataService.listUserVisibleDictData(userId, "income_type");
        Map<Long, String> dictMap = dictDataList.stream()
                .collect(Collectors.toMap(UserDictDataEntity::getId, UserDictDataEntity::getDictLabel));

        // 按照年月汇总
        Map<String, List<IncStaByYearVO>> collect = list.stream()
                .collect(Collectors.groupingBy(item -> item.getYear() + "-" + String.format("%02d", item.getMonth())));
        for (Map.Entry<String, List<IncStaByYearVO>> entry : collect.entrySet()) {
            IncStaticByYearVO incStaticByYearVO = new IncStaticByYearVO();
            String[] yearMonth = entry.getKey().split("-");
            incStaticByYearVO.setYear(Integer.parseInt(yearMonth[0]));
            incStaticByYearVO.setMonth(Integer.parseInt(yearMonth[1]));
            List<IncStaByYearVO> value = entry.getValue();
            value.forEach(item -> {
                item.setTypeName(dictMap.get(item.getTypeId()));
            });
            incStaticByYearVO.setDetail(value);
            ans.add(incStaticByYearVO);
        }
        return ApiResponse.success(ans);
    }
}