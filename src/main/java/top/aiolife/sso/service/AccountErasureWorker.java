package top.aiolife.sso.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.aiolife.sso.mapper.AccountErasureMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountErasureWorker {
    private final AccountErasureMapper mapper;
    private final AccountErasureService erasure;

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void cleanup() {
        for (Long userId : mapper.pending(LocalDateTime.now(ZoneId.of("Asia/Shanghai")))) {
            try { erasure.erase(userId); }
            catch (Exception failure) {
                mapper.retry(userId, LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusMinutes(5));
                // 不记录凭据、邮箱、文件路径或第三方异常原文。
                log.error("账号注销清理等待重试，userId={}, cause={}", userId, failure.getClass().getSimpleName());
            }
        }
    }
}
