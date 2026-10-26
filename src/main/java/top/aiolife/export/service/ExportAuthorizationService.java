package top.aiolife.export.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import top.aiolife.export.dataset.ExportDataset;
import top.aiolife.export.dataset.ExportDatasetRegistry;
import top.aiolife.export.mapper.ExportGrantMapper;
import top.aiolife.export.mapper.ExportJobMapper;
import top.aiolife.export.pojo.entity.ExportGrantEntity;
import top.aiolife.export.pojo.req.ExportAuthorizeReq;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.util.PasswordUtil;
import top.aiolife.sso.util.SecondaryPasswordPolicy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExportAuthorizationService {
    private final ExportAccessGuard access;
    private final ExportRateLimiter rate;
    private final ExportDatasetRegistry registry;
    private final ExportScope scope;
    private final ExportGrantMapper grants;
    private final ExportJobMapper jobs;
    private final UserMapper users;
    private final ExportAuditRecorder audit;

    @Transactional
    public String authorize(long userId, ExportAuthorizeReq req) {
        rate.check("verify", String.valueOf(userId), 5, 600);
        var user = users.selectForAuthUpdate(userId);
        if (user == null) throw ExportException.denied();
        boolean secondary = StringUtils.hasText(user.getSecondaryPassword());
        String stored = secondary ? user.getSecondaryPassword() : user.getPassword();
        String salt = secondary ? user.getSecondaryPasswordSalt() : user.getPasswordSalt();
        if (!StringUtils.hasText(stored))
            throw new ExportException(
                    HttpStatus.CONFLICT, "EXPORT_PASSWORD_REQUIRED", "请先在账户安全中设置登录密码或二级密码");
        if (secondary) SecondaryPasswordPolicy.validate(req.getPassword());
        String actual = PasswordUtil.encryptPassword(req.getPassword(), salt);
        if (!MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8)))
            throw new ExportException(
                    HttpStatus.FORBIDDEN,
                    "EXPORT_VERIFICATION_FAILED",
                    secondary ? "二级密码错误" : "登录密码错误");
        List<ExportDataset> datasets;
        if ("DOWNLOAD".equals(req.getPurpose())) {
            if (req.getJobId() == null) throw new IllegalArgumentException("下载核验需要任务ID");
            var job = jobs.current(req.getJobId());
            if (job == null || !job.getUserId().equals(userId)) throw ExportException.missing();
            datasets = scope.decode(job.getScopeJson());
        } else {
            if (!"CREATE".equals(req.getPurpose())) throw new IllegalArgumentException("核验用途无效");
            datasets = registry.resolve(req.getDatasetKeys());
        }
        access.check(userId, datasets, true);
        String token = UUID.randomUUID() + "-" + UUID.randomUUID();
        ExportGrantEntity grant = new ExportGrantEntity();
        grant.fillCreateCommonField(userId);
        grant.setUserId(userId);
        grant.setTokenHash(ExportHash.of(token));
        grant.setSessionHash(access.sessionHash());
        grant.setSecurityHash(ExportAccessGuard.securityHash(user));
        grant.setScopeHash(scope.hash(datasets));
        grant.setPurpose(req.getPurpose());
        grant.setJobId(req.getJobId());
        grant.setExpiresAt(ExportTime.now().plusMinutes(5));
        grant.setConsumed(0);
        grants.insert(grant);
        audit.record(userId, req.getJobId(), "AUTHORIZATION_SUCCEEDED", "OK", req.getPurpose());
        rate.clear("verify", String.valueOf(userId));
        return token;
    }

    /** 调用方事务内原子消费；建单失败随事务回滚，幂等回放不再次消费。 */
    public ExportGrantEntity consume(
            long userId, String token, String purpose, Long jobId, List<ExportDataset> datasets) {
        if (token == null || token.length() > 100) throw reauth();
        ExportGrantEntity grant =
                grants.selectOne(
                        new LambdaQueryWrapper<ExportGrantEntity>()
                                .eq(ExportGrantEntity::getTokenHash, ExportHash.of(token))
                                .eq(ExportGrantEntity::getUserId, userId));
        if (grant == null
                || !purpose.equals(grant.getPurpose())
                || !Objects.equals(jobId, grant.getJobId())
                || !grant.getSessionHash().equals(access.sessionHash())
                || !grant.getScopeHash().equals(scope.hash(datasets))
                || !grant.getSecurityHash()
                        .equals(ExportAccessGuard.securityHash(access.user(userId)))
                || !grant.getExpiresAt().isAfter(ExportTime.now())) throw reauth();
        int changed =
                grants.update(
                        null,
                        new LambdaUpdateWrapper<ExportGrantEntity>()
                                .eq(ExportGrantEntity::getId, grant.getId())
                                .eq(ExportGrantEntity::getConsumed, 0)
                                .set(ExportGrantEntity::getConsumed, 1));
        if (changed != 1) throw reauth();
        return grant;
    }

    private ExportException reauth() {
        return new ExportException(HttpStatus.FORBIDDEN, "EXPORT_REAUTH_REQUIRED", "请重新验证导出身份");
    }
}
