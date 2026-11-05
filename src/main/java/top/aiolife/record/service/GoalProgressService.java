package top.aiolife.record.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.aiolife.core.exception.SecondaryLockRequiredException;
import top.aiolife.record.mapper.IGoalMapper;
import top.aiolife.record.pojo.entity.GoalEntity;

/** 外部查询不持有数据库锁；仅回写同一用户、同一配置修订下的快照。 */
@Service
@RequiredArgsConstructor
public class GoalProgressService {
    private final IGoalMapper goals;
    private final GoalProgressEvaluator evaluator;

    public List<GoalEntity> refresh(long userId, List<Long> ids, String token) {
        if (ids == null || ids.isEmpty() || ids.size() > 10 || ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size() != ids.size())
            throw new IllegalArgumentException("每批需提供 1 至 10 个不重复的目标 ID");
        // 先校验整批归属，避免部分目标已执行后才发现越权 ID。
        var rows = goals.selectList(new LambdaQueryWrapper<GoalEntity>().eq(GoalEntity::getUserId, userId)
                .eq(GoalEntity::getIsDeleted, 0).in(GoalEntity::getId, ids));
        if (rows.size() != ids.size()) throw new IllegalArgumentException("目标不存在或无权限");
        // 每批最多两个实际查询；十二秒查询预算到期时取消排队/在途等待，避免串行十次超时。
        var timedOut = new HashSet<Long>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Void>> tasks = rows.stream().<Callable<Void>>map(row -> () -> {
                refreshOne(userId, row, token); return null;
            }).toList();
            var futures = executor.invokeAll(tasks, 12, TimeUnit.SECONDS);
            for (int i = 0; i < futures.size(); i++) {
                if (futures.get(i).isCancelled()) timedOut.add(rows.get(i).getId());
                else futures.get(i).get();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new IllegalArgumentException("进度刷新已中断");
        } catch (java.util.concurrent.ExecutionException error) {
            throw new IllegalStateException("进度快照保存失败", error.getCause());
        }
        var result = new ArrayList<GoalEntity>();
        for (var row : rows) {
            var latest = goals.selectOne(new LambdaQueryWrapper<GoalEntity>().eq(GoalEntity::getId, row.getId())
                    .eq(GoalEntity::getUserId, userId).eq(GoalEntity::getIsDeleted, 0));
            if (latest != null) {
                if (timedOut.contains(row.getId())) latest.setProgressError("本批查询超时，请重试");
                result.add(latest);
            }
        }
        return result;
    }
    private void refreshOne(long userId, GoalEntity row, String token) {
        if (!"graphql".equals(row.getProgressMode())) return;
        String refreshId = UUID.randomUUID().toString();
        int claimed = goals.update(null, new LambdaUpdateWrapper<GoalEntity>().eq(GoalEntity::getId, row.getId())
                .eq(GoalEntity::getUserId, userId).eq(GoalEntity::getIsDeleted, 0)
                .eq(GoalEntity::getProgressVersion, row.getProgressVersion())
                .set(GoalEntity::getProgressRefreshId, refreshId));
        if (claimed == 0 || Thread.currentThread().isInterrupted()) return;
        var update = new LambdaUpdateWrapper<GoalEntity>().eq(GoalEntity::getId, row.getId())
                .eq(GoalEntity::getUserId, userId).eq(GoalEntity::getIsDeleted, 0)
                .eq(GoalEntity::getProgressVersion, row.getProgressVersion())
                .eq(GoalEntity::getProgressRefreshId, refreshId);
        try {
            var computed = evaluator.evaluate(row, token);
            update.set(GoalEntity::getCurrentValue, computed.getCurrentValue())
                    .set(GoalEntity::getProgressCheckedAt, LocalDateTime.now()).set(GoalEntity::getProgressError, null);
        } catch (IllegalArgumentException | SecondaryLockRequiredException error) {
            update.set(GoalEntity::getProgressError, error.getMessage());
        }
        if (!Thread.currentThread().isInterrupted()) goals.update(null, update);
    }
}
