package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.constant.StatusConst;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.util.SysUtil;
import top.aiolife.record.mapper.IExpenseMapper;
import top.aiolife.record.pojo.entity.ExpenseEntity;
import top.aiolife.record.pojo.entity.SysDictDataEntity;
import top.aiolife.record.pojo.entity.entity.UserDictDataEntity;
import top.aiolife.record.pojo.query.ExpenseQuery;
import top.aiolife.record.pojo.req.CommonReq;
import top.aiolife.record.pojo.vo.ExpStaByYearVO;
import top.aiolife.record.pojo.vo.ExpStaticByYearVO;
import top.aiolife.record.service.IExpenseService;
import top.aiolife.record.service.UserDictDataService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * @date 2025/10/03 21:01
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/expense")
public class ExpController {

    private UserDictDataService userDictDataService;
    
    private IExpenseService expenseService;

    private IExpenseMapper expenseMapper;

    public IExpenseMapper getBaseMapper() {
        return expenseMapper;
    }

    @PostMapping("/query")
    public ApiResponse<PageResp<ExpenseEntity>> query(
            @RequestBody CommonQuery<ExpenseQuery> query) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<ExpenseEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(ExpenseEntity::getUserId, userId);
        lambdaQueryWrapper.eq(ExpenseEntity::getIsDeleted, StatusConst.NO_DELETE);
        ExpenseQuery condition = query.getCondition();
        lambdaQueryWrapper.eq(SysUtil.isNotEmpty(condition.getExpTypeId()), ExpenseEntity::getExpTypeId,
                condition.getExpTypeId());
        lambdaQueryWrapper.eq(SysUtil.isNotEmpty(condition.getPayTypeId()), ExpenseEntity::getPayTypeId,
                condition.getPayTypeId());
        lambdaQueryWrapper.likeRight(SysUtil.isNotEmpty(condition.getYear()), ExpenseEntity::getExpTime,
                condition.getYear());
        lambdaQueryWrapper.ge(condition.getStartTime() != null, ExpenseEntity::getExpTime, condition.getStartTime());
        lambdaQueryWrapper.le(condition.getEndTime() != null, ExpenseEntity::getExpTime, condition.getEndTime());
        lambdaQueryWrapper.like(SysUtil.isNotEmpty(condition.getRemark()), ExpenseEntity::getRemark, condition.getRemark());
        lambdaQueryWrapper.like(SysUtil.isNotEmpty(condition.getCounterparty()), ExpenseEntity::getCounterparty, condition.getCounterparty());
        lambdaQueryWrapper.like(SysUtil.isNotEmpty(condition.getExpDesc()), ExpenseEntity::getExpDesc, condition.getExpDesc());
        lambdaQueryWrapper.orderByDesc(ExpenseEntity::getExpTime);
        Page<ExpenseEntity> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<ExpenseEntity> iPage = expenseMapper.selectPage(page, lambdaQueryWrapper);
        PageResp<ExpenseEntity> objectPageResp = PageResp.of(iPage.getRecords(), iPage.getTotal());
        return ApiResponse.success(objectPageResp);
    }

    @PostMapping
    public ApiResponse<Boolean> insert(@RequestBody ExpenseEntity entity) {
        Long userId = StpUtil.getLoginIdAsLong();
        entity.setUserId(userId);
        // 新增时，交易金额为空时，默认设置为记账金额
        if (entity.getTransactionAmt() == null) {
            entity.setTransactionAmt(entity.getAmt());
        }
        // amt 为空的时候，默认transactionAmt
        if (entity.getAmt() == null) {
            entity.setAmt(entity.getTransactionAmt());
        }
        getBaseMapper().insert(entity);
        return ApiResponse.success(true);
    }

    @PutMapping
    public ApiResponse<Boolean> update(@RequestBody ExpenseEntity entity) {
        Long userId = StpUtil.getLoginIdAsLong();
        entity.setUserId(userId);
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
    @PostMapping("/saveBatch")
    public ApiResponse<Boolean> saveBatch(@RequestBody List<ExpenseEntity> list) {
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
                return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, 
                        "交易号已存在: " + String.join(", ", duplicatedIds));
            }
        }
        
        for (ExpenseEntity entity : list) {
            entity.setUserId(userId);
            entity.setCreateUser(userId);
            entity.setUpdateUser(userId);

            log.info("entity:{}", entity);
        }
        expenseService.saveBatch(list);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable("id") Long id) {
        LambdaQueryWrapper<ExpenseEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExpenseEntity::getId, id);
        wrapper.eq(ExpenseEntity::getUserId, StpUtil.getLoginIdAsLong());
        boolean b = getBaseMapper().delete(wrapper) > 0;
        return ApiResponse.success(b);
    }

    // 批量删除
    @PostMapping("/deleteBatch")
    public ApiResponse<Boolean> deleteBatch(@RequestBody CommonReq commonReq) {
        LambdaQueryWrapper<ExpenseEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ExpenseEntity::getId, commonReq.getIdList());
        wrapper.eq(ExpenseEntity::getUserId, StpUtil.getLoginIdAsLong());
        getBaseMapper().delete(wrapper);
        return ApiResponse.success();
    }

    /**
     * 按年度统计支出
     */
    @PostMapping("/statisticsByYear")
    public ApiResponse<Object> statisticsByYear() {
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
    @PostMapping("/statisticsByMonth")
    public ApiResponse<Object> statisticsByMonth() {
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
