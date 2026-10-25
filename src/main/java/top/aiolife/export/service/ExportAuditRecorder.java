package top.aiolife.export.service;

import lombok.RequiredArgsConstructor;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import top.aiolife.export.mapper.ExportAuditMapper;
import top.aiolife.export.pojo.entity.ExportAuditEntity;

import java.util.UUID;

/** 关键事件加入业务事务，拒绝及传输结果独立持久化；只接受安全元数据。 */
@Service
@RequiredArgsConstructor
public class ExportAuditRecorder {
    private final ExportAuditMapper mapper;

    public void record(Long userId, Long jobId, String event, String code, String details) {
        ExportAuditEntity row = new ExportAuditEntity();
        row.fillCreateCommonField(userId);
        row.setUserId(userId);
        row.setJobId(jobId);
        row.setEventType(event);
        row.setResultCode(code);
        row.setEventTime(ExportTime.now());
        row.setTraceId(MDC.get("traceId"));
        row.setDetails(details);
        row.setRequestId(UUID.randomUUID().toString());
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            row.setClientIp(attrs.getRequest().getRemoteAddr());
        }
        mapper.insert(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void independent(Long userId, Long jobId, String event, String code, String details) {
        record(userId, jobId, event, code, details);
    }
}
