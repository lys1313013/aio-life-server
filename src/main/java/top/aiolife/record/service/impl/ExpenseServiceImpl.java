package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.mybatis.IlikeQuery;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.util.SysUtil;
import top.aiolife.record.enums.DictTypeEnum;
import top.aiolife.record.mapper.IExpenseMapper;
import top.aiolife.record.pojo.entity.ExpenseEntity;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.pojo.query.ExpenseQuery;
import top.aiolife.record.pojo.vo.ExpStaByYearVO;
import top.aiolife.record.pojo.vo.ExpStaticByYearVO;
import top.aiolife.record.service.IExpenseService;
import top.aiolife.record.service.UserDictDataService;

/**
 * 支出服务实现
 *
 * @author Lys
 * @date 2025/10/05 21:42
 */
@Service
@RequiredArgsConstructor
public class ExpenseServiceImpl extends ServiceImpl<IExpenseMapper, ExpenseEntity> implements IExpenseService {

    private final UserDictDataService userDictDataService;

    @Override
    public PageResp<ExpenseEntity> pageForUser(long userId, ExpenseQuery condition, int page, int pageSize) {
        LambdaQueryWrapper<ExpenseEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExpenseEntity::getUserId, userId);
        if (condition != null) {
            if (SysUtil.isNotEmpty(condition.getExpTypeId())) {
                wrapper.eq(ExpenseEntity::getExpTypeId, IdCodec.decode(condition.getExpTypeId()));
            }
            if (SysUtil.isNotEmpty(condition.getPayTypeId())) {
                wrapper.eq(ExpenseEntity::getPayTypeId, IdCodec.decode(condition.getPayTypeId()));
            }
            if (SysUtil.isNotEmpty(condition.getYear())) {
                LocalDateTime start = LocalDate.parse(condition.getYear() + "-01-01").atStartOfDay();
                wrapper.ge(ExpenseEntity::getExpTime, start).lt(ExpenseEntity::getExpTime, start.plusYears(1));
            }
            wrapper.ge(condition.getStartTime() != null, ExpenseEntity::getExpTime, condition.getStartTime());
            wrapper.le(condition.getEndTime() != null, ExpenseEntity::getExpTime, condition.getEndTime());
            wrapper.lt(condition.getEndTimeBefore() != null, ExpenseEntity::getExpTime, condition.getEndTimeBefore());
            IlikeQuery.anyOf(wrapper, SysUtil.isNotEmpty(condition.getRemark()), condition.getRemark(),
                    ExpenseEntity::getRemark);
            IlikeQuery.anyOf(wrapper, SysUtil.isNotEmpty(condition.getCounterparty()), condition.getCounterparty(),
                    ExpenseEntity::getCounterparty);
            IlikeQuery.anyOf(wrapper, SysUtil.isNotEmpty(condition.getExpDesc()), condition.getExpDesc(),
                    ExpenseEntity::getExpDesc);
        }
        wrapper.orderByDesc(ExpenseEntity::getExpTime, ExpenseEntity::getId);
        Page<ExpenseEntity> result = baseMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return PageResp.of(result.getRecords(), result.getTotal());
    }

    @Override
    public void updateForUser(long userId, ExpenseEntity entity) {
        entity.setUserId(userId);
        entity.setUpdateUser(userId);
        entity.setUpdateTime(LocalDateTime.now());
        // 更新时，交易金额为空时，默认设置为记账金额
        if (entity.getTransactionAmt() == null) {
            entity.setTransactionAmt(entity.getAmt());
        }
        // amt 为空的时候，默认 transactionAmt
        if (entity.getAmt() == null) {
            entity.setAmt(entity.getTransactionAmt());
        }
        LambdaQueryWrapper<ExpenseEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExpenseEntity::getId, entity.getId());
        wrapper.eq(ExpenseEntity::getUserId, userId);
        baseMapper.update(entity, wrapper);
    }

    @Override
    public List<ExpStaticByYearVO> statisticsByYear(long userId) {
        List<ExpStaByYearVO> list = baseMapper.statisticsByYear(userId);
        Map<Long, String> dictMap = expenseTypeNames(userId);
        Map<Integer, List<ExpStaByYearVO>> grouped = list.stream()
                .collect(Collectors.groupingBy(ExpStaByYearVO::getYear));
        List<ExpStaticByYearVO> result = new ArrayList<>();
        for (Map.Entry<Integer, List<ExpStaByYearVO>> entry : grouped.entrySet()) {
            ExpStaticByYearVO year = new ExpStaticByYearVO();
            year.setYear(entry.getKey());
            List<ExpStaByYearVO> detail = entry.getValue();
            detail.forEach(item -> item.setTypeName(dictMap.get(item.getTypeId())));
            year.setDetail(detail);
            result.add(year);
        }
        return result;
    }

    @Override
    public List<ExpStaticByYearVO> statisticsByMonth(long userId) {
        List<ExpStaByYearVO> list = baseMapper.statisticsByMonth(userId);
        Map<Long, String> dictMap = expenseTypeNames(userId);
        Map<String, List<ExpStaByYearVO>> grouped = list.stream()
                .collect(Collectors.groupingBy(item -> item.getYear() + "-" + String.format("%02d", item.getMonth())));
        List<ExpStaticByYearVO> result = new ArrayList<>();
        for (Map.Entry<String, List<ExpStaByYearVO>> entry : grouped.entrySet()) {
            ExpStaticByYearVO month = new ExpStaticByYearVO();
            String[] yearMonth = entry.getKey().split("-");
            month.setYear(Integer.parseInt(yearMonth[0]));
            month.setMonth(Integer.parseInt(yearMonth[1]));
            List<ExpStaByYearVO> detail = entry.getValue();
            detail.forEach(item -> item.setTypeName(dictMap.get(item.getTypeId())));
            month.setDetail(detail);
            result.add(month);
        }
        return result;
    }

    private Map<Long, String> expenseTypeNames(long userId) {
        List<UserDictDataEntity> dictDataList = userDictDataService.listUserVisibleDictData(
                userId, DictTypeEnum.EXP_TYPE.getValue());
        return dictDataList.stream()
                .collect(Collectors.toMap(UserDictDataEntity::getId, UserDictDataEntity::getDictLabel));
    }
}
