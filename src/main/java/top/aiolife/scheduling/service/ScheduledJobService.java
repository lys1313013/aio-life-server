package top.aiolife.scheduling.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.core.resq.PageResp;
import top.aiolife.scheduling.convertor.ScheduledJobConvertor;
import top.aiolife.scheduling.mapper.ScheduledJobMapper;
import top.aiolife.scheduling.mapper.ScheduledJobLogMapper;
import top.aiolife.scheduling.pojo.entity.ScheduledJobEntity;
import top.aiolife.scheduling.pojo.entity.ScheduledJobLogEntity;
import top.aiolife.scheduling.pojo.req.ScheduledJobUpdateReq;
import top.aiolife.scheduling.pojo.vo.ScheduledJobVO;
import top.aiolife.scheduling.pojo.vo.ScheduledJobLogVO;

@Service
@RequiredArgsConstructor
public class ScheduledJobService {
    private final ScheduledJobMapper jobs;
    private final ScheduledJobLogMapper logs;
    private final ScheduledJobRegistry registry;
    private final ApplicationEventPublisher events;
    private final Environment environment;
    @Value("${aio.life.scheduling.enabled:true}")
    private boolean schedulerEnabled;

    /** 首次接入沿用旧配置；后续以数据库为准，不覆盖管理员修改或软删除。 */
    public void initializeDefaults() {
        seed(ScheduledJobRegistry.CHECK, "LeetCode 打卡检查",
                environment.getProperty("aio.life.server.corn.leetcodeCheck",
                        environment.getProperty("aio.life.server.leetcode-corn", "0 30 23 * * ?")));
        seed(ScheduledJobRegistry.DAILY, "LeetCode 每日一题通知",
                environment.getProperty("aio.life.server.corn.notifyTodayQuestion", "0 0 7 * * ?"));
    }

    private void seed(String code, String name, String cron) {
        if (jobs.selectCount(new LambdaQueryWrapper<ScheduledJobEntity>().eq(ScheduledJobEntity::getJobCode, code)) > 0) return;
        var row = new ScheduledJobEntity();
        row.setJobCode(code); row.setName(name);
        row.setCron("-".equals(cron) ? (ScheduledJobRegistry.CHECK.equals(code) ? "0 30 23 * * ?" : "0 0 7 * * ?")
                : ScheduledJobRules.validateCron(cron));
        row.setZoneId(ScheduledJobRules.ZONE.getId()); row.setIsEnabled(!"-".equals(cron) ? 1 : 0); row.setRevision(0);
        row.fillCreateCommonField(null);
        try { jobs.insert(row); } catch (DuplicateKeyException ignored) { /* 多实例首次启动或软删除记录不覆盖。 */ }
    }

    public ScheduledJobEntity require(Long id) {
        var row = jobs.selectById(id);
        if (row == null) throw new IllegalArgumentException("定时任务不存在");
        registry.requireKnown(row.getJobCode());
        return row;
    }

    public List<ScheduledJobVO> list() {
        return jobs.selectList(new LambdaQueryWrapper<ScheduledJobEntity>()
                .in(ScheduledJobEntity::getJobCode, registry.codes()).orderByAsc(ScheduledJobEntity::getId))
                .stream().map(this::toVO).toList();
    }

    @Transactional
    public ScheduledJobVO update(Long id, ScheduledJobUpdateReq req, Long operator) {
        require(id);
        String cron = ScheduledJobRules.validateCron(req.getCron());
        if (req.getIsEnabled() == null || req.getRevision() == null || req.getRevision() < 0) {
            throw new IllegalArgumentException("启用状态和配置版本必填");
        }
        int changed = jobs.update(null, new LambdaUpdateWrapper<ScheduledJobEntity>()
                .eq(ScheduledJobEntity::getId, id).eq(ScheduledJobEntity::getRevision, req.getRevision())
                .set(ScheduledJobEntity::getCron, cron).set(ScheduledJobEntity::getIsEnabled, req.getIsEnabled())
                .set(ScheduledJobEntity::getRevision, Math.addExact(req.getRevision(), 1))
                .set(ScheduledJobEntity::getUpdateUser, operator)
                .set(ScheduledJobEntity::getUpdateTime, LocalDateTime.now(ScheduledJobRules.ZONE)));
        if (changed == 0) throw new IllegalArgumentException("配置已被修改，请刷新后重试");
        events.publishEvent(new ScheduledJobChanged(id));
        return toVO(require(id));
    }

    public PageResp<ScheduledJobLogVO> logs(Long id, int page, int pageSize) {
        require(id);
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("分页参数无效");
        var result = logs.selectPage(new Page<>(page, pageSize), new LambdaQueryWrapper<ScheduledJobLogEntity>()
                .eq(ScheduledJobLogEntity::getJobId, id)
                .orderByDesc(ScheduledJobLogEntity::getStartTime, ScheduledJobLogEntity::getId));
        return PageResp.of(result.getRecords().stream().map(ScheduledJobConvertor.INSTANCE::toLogVO).toList(), result.getTotal());
    }

    private ScheduledJobVO toVO(ScheduledJobEntity row) {
        var vo = ScheduledJobConvertor.INSTANCE.toVO(row);
        vo.setSchedulerEnabled(schedulerEnabled);
        if (schedulerEnabled && Integer.valueOf(1).equals(row.getIsEnabled())) {
            var next = CronExpression.parse(row.getCron()).next(ZonedDateTime.now(ScheduledJobRules.ZONE));
            vo.setNextExecutionTime(next == null ? null : next.toLocalDateTime());
        }
        return vo;
    }
}
