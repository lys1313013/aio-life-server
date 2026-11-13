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
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.util.SysUtil;
import top.aiolife.record.enums.DictTypeEnum;
import top.aiolife.record.mapper.IIncomeMapper;
import top.aiolife.record.pojo.entity.IncomeEntity;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.pojo.query.IncomeQuery;
import top.aiolife.record.pojo.vo.IncStaByYearVO;
import top.aiolife.record.pojo.vo.IncStaticByYearVO;
import top.aiolife.record.service.IIncomeService;
import top.aiolife.record.service.UserDictDataService;

/**
 * 收入服务实现
 *
 * @author Lys
 * @date 2025/09/14 21:09
 */
@Service
@RequiredArgsConstructor
public class IncomeServiceImpl extends ServiceImpl<IIncomeMapper, IncomeEntity> implements IIncomeService {

    private final UserDictDataService userDictDataService;

    @Override
    public PageResp<IncomeEntity> pageForUser(long userId, IncomeQuery condition, int page, int pageSize) {
        LambdaQueryWrapper<IncomeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IncomeEntity::getUserId, userId);
        if (condition != null) {
            if (SysUtil.isNotEmpty(condition.getIncTypeId())) {
                wrapper.eq(IncomeEntity::getIncTypeId, IdCodec.decode(condition.getIncTypeId()));
            }
            if (SysUtil.isNotEmpty(condition.getYear())) {
                LocalDate start = LocalDate.parse(condition.getYear() + "-01-01");
                wrapper.ge(IncomeEntity::getIncDate, start).lt(IncomeEntity::getIncDate, start.plusYears(1));
            }
            wrapper.ge(SysUtil.isNotEmpty(condition.getStartTime()), IncomeEntity::getIncDate,
                    condition.getStartTime());
            wrapper.le(SysUtil.isNotEmpty(condition.getEndTime()), IncomeEntity::getIncDate, condition.getEndTime());
            wrapper.lt(SysUtil.isNotEmpty(condition.getEndTimeBefore()), IncomeEntity::getIncDate,
                    condition.getEndTimeBefore());
        }
        wrapper.orderByDesc(IncomeEntity::getIncDate, IncomeEntity::getId);
        Page<IncomeEntity> result = baseMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return PageResp.of(result.getRecords(), result.getTotal());
    }

    @Override
    public boolean createForUser(long userId, IncomeEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        entity.setId(null);
        entity.setUserId(userId);
        entity.setIsDeleted(0);
        entity.setCreateUser(userId);
        entity.setCreateTime(now);
        entity.setUpdateUser(userId);
        entity.setUpdateTime(now);
        return baseMapper.insert(entity) > 0;
    }

    @Override
    public boolean updateForUser(long userId, long incomeId, IncomeEntity entity) {
        entity.setId(incomeId);
        entity.setUserId(null);
        entity.setUpdateUser(userId);
        entity.setUpdateTime(LocalDateTime.now());
        LambdaQueryWrapper<IncomeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IncomeEntity::getId, incomeId);
        wrapper.eq(IncomeEntity::getUserId, userId);
        return baseMapper.update(entity, wrapper) > 0;
    }

    @Override
    public boolean deleteForUser(long userId, long incomeId) {
        LambdaQueryWrapper<IncomeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IncomeEntity::getId, incomeId);
        wrapper.eq(IncomeEntity::getUserId, userId);
        return baseMapper.delete(wrapper) > 0;
    }

    @Override
    public List<IncStaticByYearVO> statisticsByYear(long userId) {
        List<IncStaByYearVO> list = baseMapper.statisticsByYear(userId);
        Map<Long, String> dictMap = incomeTypeNames(userId);
        Map<Integer, List<IncStaByYearVO>> grouped = list.stream()
                .collect(Collectors.groupingBy(IncStaByYearVO::getYear));
        List<IncStaticByYearVO> result = new ArrayList<>();
        for (Map.Entry<Integer, List<IncStaByYearVO>> entry : grouped.entrySet()) {
            IncStaticByYearVO year = new IncStaticByYearVO();
            year.setYear(entry.getKey());
            List<IncStaByYearVO> detail = entry.getValue();
            detail.forEach(item -> item.setTypeName(dictMap.get(item.getTypeId())));
            year.setDetail(detail);
            result.add(year);
        }
        return result;
    }

    @Override
    public List<IncStaticByYearVO> statisticsByMonth(long userId) {
        List<IncStaByYearVO> list = baseMapper.statisticsByMonth(userId);
        Map<Long, String> dictMap = incomeTypeNames(userId);
        Map<String, List<IncStaByYearVO>> grouped = list.stream()
                .collect(Collectors.groupingBy(item -> item.getYear() + "-" + String.format("%02d", item.getMonth())));
        List<IncStaticByYearVO> result = new ArrayList<>();
        for (Map.Entry<String, List<IncStaByYearVO>> entry : grouped.entrySet()) {
            IncStaticByYearVO month = new IncStaticByYearVO();
            String[] yearMonth = entry.getKey().split("-");
            month.setYear(Integer.parseInt(yearMonth[0]));
            month.setMonth(Integer.parseInt(yearMonth[1]));
            List<IncStaByYearVO> detail = entry.getValue();
            detail.forEach(item -> item.setTypeName(dictMap.get(item.getTypeId())));
            month.setDetail(detail);
            result.add(month);
        }
        return result;
    }

    private Map<Long, String> incomeTypeNames(long userId) {
        List<UserDictDataEntity> dictDataList = userDictDataService.listUserVisibleDictData(
                userId, DictTypeEnum.INCOME_TYPE.getValue());
        return dictDataList.stream()
                .collect(Collectors.toMap(UserDictDataEntity::getId, UserDictDataEntity::getDictLabel));
    }
}
