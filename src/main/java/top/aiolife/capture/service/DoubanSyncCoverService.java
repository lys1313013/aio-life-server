package top.aiolife.capture.service;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import top.aiolife.record.enums.AttachmentBizType;
import top.aiolife.record.service.IAttachmentService;

/** 封面上传使用独立事务，失败不会使影片状态同步事务回滚。 */
@Service
@RequiredArgsConstructor
public class DoubanSyncCoverService {
    private final IAttachmentService files;
    private final CaptureSecurity access;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String upload(long user, String url) {
        // 复用既有豆瓣CDN白名单、禁止跳转、图片内容和大小校验。
        var uploaded = files.uploadFromUrl(url, AttachmentBizType.MOVIE);
        if (uploaded == null || uploaded.getId() == null || uploaded.getId().isBlank()) {
            throw new IllegalStateException("封面上传未返回文件ID");
        }
        access.file(user, uploaded.getId(), AttachmentBizType.MOVIE.getBizType());
        return uploaded.getId();
    }
}
