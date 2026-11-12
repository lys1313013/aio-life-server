package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.validation.annotation.Validated;
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
import top.aiolife.record.mapper.IIncomeMapper;
import top.aiolife.record.pojo.entity.IncomeEntity;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.pojo.query.IncomeQuery;
import top.aiolife.record.pojo.req.IncomeCreateReq;
import top.aiolife.record.pojo.req.IncomeUpdateReq;
import top.aiolife.record.pojo.vo.IncStaByYearVO;
import top.aiolife.record.pojo.vo.IncStaticByYearVO;
import top.aiolife.record.pojo.vo.IncomeVO;
import top.aiolife.record.service.UserDictDataService;

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


    @Operation(summary = "分页查询收入记录")
    @GetMapping("/query")
    public ApiResponse<PageResp<IncomeVO>> query(
            @QueryParams CommonQuery<IncomeQuery> query) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<IncomeEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(IncomeEntity::getUserId, userId);
        IncomeQuery condition = query.getCondition();
        lambdaQueryWrapper.eq(SysUtil.isNotEmpty(condition.getIncTypeId()), IncomeEntity::getIncTypeId,
                condition.getIncTypeId());
        if (SysUtil.isNotEmpty(condition.getYear())) {
            LocalDate start = LocalDate.parse(condition.getYear() + "-01-01");
            lambdaQueryWrapper.ge(IncomeEntity::getIncDate, start)
                    .lt(IncomeEntity::getIncDate, start.plusYears(1));
        }
        lambdaQueryWrapper.ge(SysUtil.isNotEmpty(condition.getStartTime()), IncomeEntity::getIncDate, condition.getStartTime());
        lambdaQueryWrapper.le(SysUtil.isNotEmpty(condition.getEndTime()), IncomeEntity::getIncDate, condition.getEndTime());
        lambdaQueryWrapper.lt(SysUtil.isNotEmpty(condition.getEndTimeBefore()), IncomeEntity::getIncDate, condition.getEndTimeBefore());
        lambdaQueryWrapper.orderByDesc(IncomeEntity::getIncDate, IncomeEntity::getId);
        Page<IncomeEntity> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<IncomeEntity> iPage = incomeMapper.selectPage(page, lambdaQueryWrapper);
        PageResp<IncomeEntity> objectPageResp = PageResp.of(iPage.getRecords(), iPage.getTotal());
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toIncomeVOPage(objectPageResp));
    }


    @Operation(summary = "新增收入记录")
    @PostMapping
    public ApiResponse<Boolean> add(@Validated @RequestBody IncomeCreateReq entityReq) {
        IncomeEntity entity = RecordApiConvertor.INSTANCE.fromIncomeCreateReq(entityReq);
        entity.setId(null);
        entity.setUserId(StpUtil.getLoginIdAsLong());
        return ApiResponse.success(getBaseMapper().insert(entity) > 0);
    }

    @Operation(summary = "更新收入记录")
    @PutMapping("/{incomeId}")
    public ApiResponse<Boolean> update(@PathVariable("incomeId") Long incomeId, @Validated @RequestBody IncomeUpdateReq entityReq) {
        IncomeEntity entity = RecordApiConvertor.INSTANCE.fromIncomeUpdateReq(entityReq);
        Long userId = StpUtil.getLoginIdAsLong();
        entity.setId(incomeId);
        entity.setUserId(null);
        entity.setUpdateUser(userId);
        entity.setUpdateTime(LocalDateTime.now());
        LambdaQueryWrapper<IncomeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IncomeEntity::getId, incomeId);
        wrapper.eq(IncomeEntity::getUserId, userId);
        return ApiResponse.success(getBaseMapper().update(entity, wrapper) > 0);
    }

    @Operation(summary = "删除收入记录")
    @DeleteMapping("/{incomeId}")
    public ApiResponse<Boolean> delete(@PathVariable("incomeId") Long incomeId) {
        LambdaQueryWrapper<IncomeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IncomeEntity::getId, incomeId);
        wrapper.eq(IncomeEntity::getUserId, StpUtil.getLoginIdAsLong());
        boolean b = getBaseMapper().delete(wrapper) > 0;
        return ApiResponse.success(b);
    }

    @Operation(summary = "按年度和分类统计收入", description = "统计当前用户全部历史，返回分类名称和金额。")
    @GetMapping("/statisticsByYear")
    public ApiResponse<List<IncStaticByYearVO>> statisticsByYear() {
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

    @Operation(summary = "按月份和分类统计收入", description = "统计当前用户全部历史，按年月分组，不接收日期筛选。")
    @GetMapping("/statisticsByMonth")
    public ApiResponse<List<IncStaticByYearVO>> statisticsByMonth() {
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
