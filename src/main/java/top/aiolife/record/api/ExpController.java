package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.constant.StatusConst;
import top.aiolife.core.mybatis.IlikeQuery;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.util.SysUtil;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.mapper.IExpenseMapper;
import top.aiolife.record.pojo.entity.ExpenseEntity;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.pojo.query.ExpenseQuery;
import top.aiolife.record.pojo.req.CommonReq;
import top.aiolife.record.pojo.req.ExpenseCreateReq;
import top.aiolife.record.pojo.req.ExpenseUpdateReq;
import top.aiolife.record.pojo.vo.ExpStaByYearVO;
import top.aiolife.record.pojo.vo.ExpStaticByYearVO;
import top.aiolife.record.pojo.vo.ExpenseVO;
import top.aiolife.record.service.DeviceExpenseService;
import top.aiolife.record.service.IExpenseService;
import top.aiolife.record.service.UserDictDataService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/10/03 21:01
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/expense")
public class ExpController {

    private UserDictDataService userDictDataService;

    private IExpenseService expenseService;

    private DeviceExpenseService deviceExpenseService;

    private IExpenseMapper expenseMapper;

    public IExpenseMapper getBaseMapper() {
        return expenseMapper;
    }

    @Operation(summary = "分页查询支出记录")
    @GetMapping("/query")
    public ApiResponse<PageResp<ExpenseVO>> query(
            @QueryParams CommonQuery<ExpenseQuery> query) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<ExpenseEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(ExpenseEntity::getUserId, userId);
        lambdaQueryWrapper.eq(ExpenseEntity::getIsDeleted, StatusConst.NO_DELETE);
        ExpenseQuery condition = query.getCondition();
        lambdaQueryWrapper.eq(SysUtil.isNotEmpty(condition.getExpTypeId()), ExpenseEntity::getExpTypeId,
                condition.getExpTypeId());
        lambdaQueryWrapper.eq(SysUtil.isNotEmpty(condition.getPayTypeId()), ExpenseEntity::getPayTypeId,
                condition.getPayTypeId());
        if (SysUtil.isNotEmpty(condition.getYear())) {
            LocalDateTime start = LocalDate.parse(condition.getYear() + "-01-01").atStartOfDay();
            lambdaQueryWrapper.ge(ExpenseEntity::getExpTime, start)
                    .lt(ExpenseEntity::getExpTime, start.plusYears(1));
        }
        lambdaQueryWrapper.ge(condition.getStartTime() != null, ExpenseEntity::getExpTime, condition.getStartTime());
        lambdaQueryWrapper.le(condition.getEndTime() != null, ExpenseEntity::getExpTime, condition.getEndTime());
        lambdaQueryWrapper.lt(condition.getEndTimeBefore() != null, ExpenseEntity::getExpTime, condition.getEndTimeBefore());
        IlikeQuery.anyOf(lambdaQueryWrapper, SysUtil.isNotEmpty(condition.getRemark()), condition.getRemark(), ExpenseEntity::getRemark);
        IlikeQuery.anyOf(lambdaQueryWrapper, SysUtil.isNotEmpty(condition.getCounterparty()), condition.getCounterparty(), ExpenseEntity::getCounterparty);
        IlikeQuery.anyOf(lambdaQueryWrapper, SysUtil.isNotEmpty(condition.getExpDesc()), condition.getExpDesc(), ExpenseEntity::getExpDesc);
        lambdaQueryWrapper.orderByDesc(ExpenseEntity::getExpTime, ExpenseEntity::getId);
        Page<ExpenseEntity> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<ExpenseEntity> iPage = expenseMapper.selectPage(page, lambdaQueryWrapper);
        PageResp<ExpenseEntity> objectPageResp = PageResp.of(iPage.getRecords(), iPage.getTotal());
        var result = RecordApiConvertor.INSTANCE.toExpenseVOPage(objectPageResp);
        deviceExpenseService.enrich(userId, result.getItems());
        return ApiResponse.success(result);
    }

    @Operation(summary = "新增支出记录")
    @PostMapping
    public ApiResponse<Boolean> insert(@Valid @RequestBody ExpenseCreateReq entityReq) {
        return ApiResponse.success(deviceExpenseService.createExpenses(StpUtil.getLoginIdAsLong(), List.of(entityReq)));
    }

    @Operation(summary = "更新支出记录")
    @PutMapping
    public ApiResponse<Boolean> update(@Valid @RequestBody ExpenseUpdateReq entityReq) {
        ExpenseEntity entity = RecordApiConvertor.INSTANCE.fromExpenseUpdateReq(entityReq);
        Long userId = StpUtil.getLoginIdAsLong();
        entity.setUserId(userId);
        entity.setUpdateUser(userId);
        entity.setUpdateTime(LocalDateTime.now());
        // 更新时，交易金额为空时，默认设置为记账金额
        if (entity.getTransactionAmt() == null) {
            entity.setTransactionAmt(entity.getAmt());
        }
        // amt 为空的时候，默认transactionAmt
        if (entity.getAmt() == null) {
            entity.setAmt(entity.getTransactionAmt());
        }
        LambdaQueryWrapper<ExpenseEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExpenseEntity::getId, entity.getId());
        wrapper.eq(ExpenseEntity::getUserId, userId);
        getBaseMapper().update(entity, wrapper);
        return ApiResponse.success(true);
    }

    // 批量新增
    @Operation(summary = "批量新增支出记录")
    @PostMapping("/saveBatch")
    public ApiResponse<Boolean> saveBatch(@Valid @RequestBody List<@Valid ExpenseCreateReq> requests) {
        List<ExpenseEntity> list = requests.stream().map(RecordApiConvertor.INSTANCE::fromExpenseCreateReq).toList();
        long userId = StpUtil.getLoginIdAsLong();

        // 提取所有有交易号的记录
        List<String> transactionIds = list.stream()
                .map(ExpenseEntity::getTransactionId)
                .filter(tid -> tid != null && !tid.trim().isEmpty())
                .distinct()
                .toList();

        // 用户级别验重：检查交易号是否已存在
        if (!transactionIds.isEmpty()) {
            LambdaQueryWrapper<ExpenseEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ExpenseEntity::getUserId, userId);
            wrapper.in(ExpenseEntity::getTransactionId, transactionIds);
            List<ExpenseEntity> existingList = getBaseMapper().selectList(wrapper);

            if (!existingList.isEmpty()) {
                List<String> duplicatedIds = existingList.stream()
                        .map(ExpenseEntity::getTransactionId)
                        .toList();
                return ApiResponse.error(ResponseCodeConst.COMMON_FAIL,
                        "交易号已存在: " + String.join(", ", duplicatedIds));
            }
        }

        deviceExpenseService.createExpenses(userId, requests);
        return ApiResponse.success();
    }

    @Operation(summary = "删除支出记录")
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable("id") Long id) {
        return ApiResponse.success(deviceExpenseService.deleteExpenses(StpUtil.getLoginIdAsLong(), List.of(id)));
    }

    @Operation(summary = "批量删除支出记录")
    @PostMapping("/deleteBatch")
    public ApiResponse<Boolean> deleteBatch(@RequestBody CommonReq commonReq) {
        return ApiResponse.success(deviceExpenseService.deleteExpenses(StpUtil.getLoginIdAsLong(), commonReq.getIdList() == null ? List.of() : commonReq.getIdList().stream().map(Long::valueOf).toList()));
    }

    /**
     * 按年度统计支出
     */
    @Operation(summary = "按年度和分类统计支出", description = "统计当前用户全部历史，返回分类名称和金额。")
    @GetMapping("/statisticsByYear")
    public ApiResponse<List<ExpStaticByYearVO>> statisticsByYear() {
        long userId = StpUtil.getLoginIdAsLong();
        List<ExpStaByYearVO> list = expenseMapper.statisticsByYear(userId);
        List<ExpStaticByYearVO> ans = new ArrayList<>();

        // 获取收入类型字典数据
        List<UserDictDataEntity> dictDataList = userDictDataService.listUserVisibleDictData(userId, "exp_type");
        Map<Long, String> dictMap = dictDataList.stream()
                .collect(Collectors.toMap(UserDictDataEntity::getId, UserDictDataEntity::getDictLabel));

        // 按照年度汇总
        Map<Integer, List<ExpStaByYearVO>> collect = list.stream()
                .collect(Collectors.groupingBy(ExpStaByYearVO::getYear));
        for (Map.Entry<Integer, List<ExpStaByYearVO>> entry : collect.entrySet()) {
            ExpStaticByYearVO expStaticByYearVO = new ExpStaticByYearVO();
            expStaticByYearVO.setYear(entry.getKey());
            List<ExpStaByYearVO> value = entry.getValue();
            value.forEach(item -> {
                item.setTypeName(dictMap.get(item.getTypeId()));
            });
            expStaticByYearVO.setDetail(value);
            ans.add(expStaticByYearVO);
        }
        return ApiResponse.success(ans);
    }

    /**
     * 按月度统计支出
     */
    @Operation(summary = "按月份和分类统计支出", description = "统计当前用户全部历史，按年月分组，不接收日期筛选。")
    @GetMapping("/statisticsByMonth")
    public ApiResponse<List<ExpStaticByYearVO>> statisticsByMonth() {
        long userId = StpUtil.getLoginIdAsLong();
        List<ExpStaByYearVO> list = expenseMapper.statisticsByMonth(userId);
        List<ExpStaticByYearVO> ans = new ArrayList<>();

        // 获取收入类型字典数据
        List<UserDictDataEntity> dictDataList = userDictDataService.listUserVisibleDictData(userId, "exp_type");
        Map<Long, String> dictMap = dictDataList.stream()
                .collect(Collectors.toMap(UserDictDataEntity::getId, UserDictDataEntity::getDictLabel));

        // 按照年月汇总
        Map<String, List<ExpStaByYearVO>> collect = list.stream()
                .collect(Collectors.groupingBy(item -> item.getYear() + "-" + String.format("%02d", item.getMonth())));
        for (Map.Entry<String, List<ExpStaByYearVO>> entry : collect.entrySet()) {
            ExpStaticByYearVO expStaticByYearVO = new ExpStaticByYearVO();
            String[] yearMonth = entry.getKey().split("-");
            expStaticByYearVO.setYear(Integer.parseInt(yearMonth[0]));
            expStaticByYearVO.setMonth(Integer.parseInt(yearMonth[1]));
            List<ExpStaByYearVO> value = entry.getValue();
            value.forEach(item -> {
                item.setTypeName(dictMap.get(item.getTypeId()));
            });
            expStaticByYearVO.setDetail(value);
            ans.add(expStaticByYearVO);
        }
        return ApiResponse.success(ans);
    }
}
