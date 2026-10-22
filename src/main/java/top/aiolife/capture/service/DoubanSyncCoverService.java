package top.aiolife.capture.service;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import top.aiolife.record.enums.FileBizType;
import top.aiolife.record.service.IFileService;

/** 封面上传使用独立事务，失败不会使影片状态同步事务回滚。 */
@Service
@RequiredArgsConstructor
public class DoubanSyncCoverService {
    private final IFileService files;
    private final CaptureSecurity access;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String upload(long user, String url) {
        // 复用既有豆瓣CDN白名单、禁止跳转、图片内容和大小校验。
        var uploaded = files.uploadFromUrl(url, FileBizType.MOVIE);
        if (uploaded == null || uploaded.getId() == null || uploaded.getId().isBlank()) {
            throw new IllegalStateException("封面上传未返回文件ID");
        }
        access.file(user, uploaded.getId(), FileBizType.MOVIE.getBizType());
        return uploaded.getId();
    }
}
