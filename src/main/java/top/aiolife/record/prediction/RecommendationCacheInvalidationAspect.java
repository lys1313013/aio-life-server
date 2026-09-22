package top.aiolife.record.prediction;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 数据写入成功后失效；事务提交后再次清理，避免提交前查询回填旧数据。 */
@Aspect
@Component
@RequiredArgsConstructor
public class RecommendationCacheInvalidationAspect {
    private final RecommendationDataCache cache;

    @AfterReturning("execution(* top.aiolife.record.service.impl.TimeRecordServiceImpl.saveTimeRecord(..)) || "
            + "execution(* top.aiolife.record.service.impl.TimeRecordServiceImpl.updateTimeRecord(..)) || "
            + "execution(* top.aiolife.record.service.impl.TimeRecordServiceImpl.removeById(..)) || "
            + "execution(* top.aiolife.record.service.impl.TimeRecordServiceImpl.removeByDate(..)) || "
            + "execution(* top.aiolife.record.service.impl.TimeTrackerCategoryServiceImpl.createCategory(..)) || "
            + "execution(* top.aiolife.record.service.impl.TimeTrackerCategoryServiceImpl.updateCategory(..)) || "
            + "execution(* top.aiolife.record.service.impl.TimeTrackerCategoryServiceImpl.deleteCategory(..)) || "
            + "execution(* top.aiolife.record.service.impl.TimeTrackerCategoryServiceImpl.admin*(..)) || "
            + "execution(* top.aiolife.system.service.impl.WorkCalendarServiceImpl.evictPreviousComparableDate(..))")
    public void invalidate() {
        cache.invalidate();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { cache.invalidate(); }
            });
        }
    }
}
