package top.aiolife.export.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import top.aiolife.core.resq.PageResp;
import top.aiolife.export.mapper.ExportAuditMapper;
import top.aiolife.export.pojo.entity.ExportAuditEntity;
import top.aiolife.sso.service.SecondaryLockGuard;

/**
 * 数据导出审计只读查询。
 *
 * @author Lys
 * @date 2026/11/13
 */
@Service
@RequiredArgsConstructor
public class ExportAuditService {
    private final ExportAuditMapper mapper;
    private final ExportAccessGuard access;
    private final SecondaryLockGuard locks;

    public PageResp<ExportAuditEntity> page(long actorId, int page, int pageSize, Long userId) {
        long sessionUser = access.requestUser();
        if (sessionUser != actorId) throw ExportException.denied();
        locks.checkMenus(actorId, "/system/operation-log");
        if (page < 1 || pageSize < 1 || pageSize > 100)
            throw new IllegalArgumentException("分页参数无效");
        var result =
                mapper.selectPage(
                        new Page<>(page, pageSize),
                        new LambdaQueryWrapper<ExportAuditEntity>()
                                .eq(userId != null, ExportAuditEntity::getUserId, userId)
                                .orderByDesc(
                                        ExportAuditEntity::getEventTime, ExportAuditEntity::getId));
        return PageResp.of(result.getRecords(), result.getTotal());
    }
}
