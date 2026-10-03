package top.aiolife.record.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.aiolife.record.mapper.IBVideoMapper;
import top.aiolife.record.mapper.IFileMapper;
import top.aiolife.record.mapper.ImageImportTaskMapper;
import top.aiolife.record.pojo.entity.BVideoEntity;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.pojo.entity.ImageImportTaskEntity;

@Service
@RequiredArgsConstructor
public class VideoCoverService {
    private final IBVideoMapper videos;
    private final ImageImportTaskMapper tasks;
    private final IFileMapper files;
    private final VideoCoverDownloader downloader;
    private final VideoCoverStorage storage;

    /** 在调用方业务事务内执行。源地址不变时不重复排队；重试显式产生新版本。 */
    @Transactional
    public void enqueue(long videoId, long owner, String requestedSource, boolean force) {
        BVideoEntity video = videos.lockOwned(videoId, owner);
        if (video == null) throw new IllegalArgumentException("视频不存在或无权限");
        String source = requestedSource == null ? video.getCover() : requestedSource.trim();
        if (!force && Objects.equals(source, video.getCover()) && !"NONE".equals(video.getCoverState())
                && video.getCoverState() != null) return;
        if (StringUtils.hasText(source)) downloader.normalize(source);
        long version = (video.getCoverVersion() == null ? 0 : video.getCoverVersion()) + 1;
        var update = new LambdaUpdateWrapper<BVideoEntity>().eq(BVideoEntity::getId, videoId)
                .set(BVideoEntity::getCover, source).set(BVideoEntity::getCoverVersion, version)
                .set(BVideoEntity::getCoverState, StringUtils.hasText(source) ? "PENDING" : "NONE");
        if (!StringUtils.hasText(source)) update.set(BVideoEntity::getCoverFileId, null);
        videos.update(null, update);
        if (!StringUtils.hasText(source)) return;
        var task = new ImageImportTaskEntity();
        task.setVideoId(videoId); task.setCoverVersion(version); task.setSourceUrl(source);
        task.setState("PENDING"); task.setAttempts(0); task.setNextAttemptAt(LocalDateTime.now());
        task.fillCreateCommonField(owner);
        tasks.insert(task);
    }

    @Transactional
    public void retry(long videoId, long owner) {
        var video = videos.lockOwned(videoId, owner);
        if (video == null) throw new IllegalArgumentException("视频不存在或无权限");
        if (!"FAILED".equals(video.getCoverState())) return;
        enqueue(videoId, owner, video.getCover(), true);
    }

    @Transactional
    public void complete(long taskId, String token, byte[] bytes) {
        var task = tasks.lockClaim(taskId, token);
        if (task == null) return;
        var video = videos.lockOwned(task.getVideoId(), task.getCreateUser());
        if (video == null || !Objects.equals(video.getCoverVersion(), task.getCoverVersion())) {
            finish(taskId, "CANCELLED", null); return;
        }
        var object = storage.store(bytes, video.getUserId());
        var file = new FileEntity();
        file.setStorageObjectId(object.getId()); file.setFileName(object.getObjectKey());
        file.setFileSize(object.getFileSize()); file.setFileType(object.getContentType());
        file.setHashValue(object.getSha256()); file.setBizType("b_video_cover");
        file.setBizId(video.getId()); file.setIsPublic(0); file.fillCreateCommonField(video.getUserId());
        files.insert(file);
        videos.update(null, new LambdaUpdateWrapper<BVideoEntity>().eq(BVideoEntity::getId, video.getId())
                .set(BVideoEntity::getCoverFileId, file.getId()).set(BVideoEntity::getCoverState, "READY"));
        finish(taskId, "READY", null);
    }

    @Transactional
    public void failed(long taskId, String token, String errorCode) {
        var task = tasks.lockClaim(taskId, token);
        if (task == null) return;
        var video = videos.lockOwned(task.getVideoId(), task.getCreateUser());
        if (video == null || !Objects.equals(video.getCoverVersion(), task.getCoverVersion())) {
            finish(taskId, "CANCELLED", null); return;
        }
        boolean exhausted = task.getAttempts() >= 5;
        tasks.update(null, new LambdaUpdateWrapper<ImageImportTaskEntity>().eq(ImageImportTaskEntity::getId, taskId)
                .set(ImageImportTaskEntity::getState, exhausted ? "FAILED" : "PENDING")
                .set(ImageImportTaskEntity::getErrorCode, errorCode)
                .set(ImageImportTaskEntity::getNextAttemptAt, LocalDateTime.now().plusSeconds(15L << task.getAttempts()))
                .set(ImageImportTaskEntity::getLeaseToken, null).set(ImageImportTaskEntity::getLeaseUntil, null));
        if (exhausted) videos.update(null, new LambdaUpdateWrapper<BVideoEntity>().eq(BVideoEntity::getId, video.getId())
                .set(BVideoEntity::getCoverState, "FAILED"));
    }

    private void finish(long taskId, String state, String error) {
        tasks.update(null, new LambdaUpdateWrapper<ImageImportTaskEntity>().eq(ImageImportTaskEntity::getId, taskId)
                .set(ImageImportTaskEntity::getState, state).set(ImageImportTaskEntity::getErrorCode, error)
                .set(ImageImportTaskEntity::getLeaseToken, null).set(ImageImportTaskEntity::getLeaseUntil, null));
    }

}
