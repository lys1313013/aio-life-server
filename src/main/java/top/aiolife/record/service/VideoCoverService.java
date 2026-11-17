package top.aiolife.record.service;

import java.util.HashSet;
import java.util.Set;
import top.aiolife.record.pojo.entity.ExternalResourceEntity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.aiolife.record.mapper.IBVideoMapper;
import top.aiolife.record.mapper.IAttachmentMapper;
import top.aiolife.record.mapper.ImageImportTaskMapper;
import top.aiolife.record.mapper.ExternalResourceMapper;
import top.aiolife.record.pojo.entity.BVideoEntity;
import top.aiolife.record.pojo.entity.AttachmentEntity;
import top.aiolife.record.pojo.entity.ImageImportTaskEntity;

/** 按业务记录行锁串行化更换、清空、重试和发布；下载状态只保存在来源。 */
@Service
@RequiredArgsConstructor
public class VideoCoverService {
    private static final String TYPE="b_video_cover";
    private final IBVideoMapper videos;
    private final ImageImportTaskMapper tasks;
    private final IAttachmentMapper files;
    private final ExternalResourceMapper sources;
    private final ExternalResourceService resources;
    private final VideoCoverStorage storage;

    @Transactional
    public void enqueue(long videoId,long owner,String requestedSource,boolean retry) {
        var video=videos.lockOwned(videoId,owner);
        if(video==null) throw new IllegalArgumentException("视频不存在或无权限");
        if(requestedSource==null) return;
        if(!StringUtils.hasText(requestedSource)) { cancel(videoId,owner); detach(videoId,owner); return; }
        var source=resources.video(requestedSource,owner);
        var previous=tasks.latest(TYPE,videoId);
        var current=files.selectOne(new LambdaQueryWrapper<AttachmentEntity>().eq(AttachmentEntity::getBizType,TYPE).eq(AttachmentEntity::getBizId,videoId));
        if(!retry && previous!=null && Objects.equals(previous.getExternalResourceId(),source.getId()) && Set.of("PENDING","RUNNING","FAILED").contains(previous.getState())) return;
        if(current!=null && Objects.equals(current.getExternalResourceId(),source.getId()) && source.getStorageObjectId()!=null) {
            cancel(videoId,owner); return;
        }
        cancel(videoId,owner);
        if(source.getStorageObjectId()!=null) { attach(videoId,owner,source.getId(),source.getStorageObjectId()); return; }
        if(retry) resources.retryFailed(source.getId());
        var task=new ImageImportTaskEntity(); task.fillCreateCommonField(owner);
        task.setBizType(TYPE); task.setBizId(videoId); task.setExternalResourceId(source.getId());
        task.setState("FAILED".equals(source.getState()) && source.getAttempts()>=5 && !retry?"FAILED":"PENDING");
        tasks.insert(task);
    }

    @Transactional
    public void retry(long videoId,long owner) {
        if(videos.lockOwned(videoId,owner)==null) throw new IllegalArgumentException("视频不存在或无权限");
        var task=tasks.latest(TYPE,videoId);
        if(task==null || !"FAILED".equals(task.getState())) return;
        var source=sources.selectById(task.getExternalResourceId());
        if(source!=null) enqueue(videoId,owner,source.getSourceUrl(),true);
    }

    @Transactional
    public void cancel(long videoId,long owner) { tasks.cancel(TYPE,videoId,owner); }

    @Transactional
    public void delete(long videoId,long owner) {
        if(videos.lockOwned(videoId,owner)==null) throw new IllegalArgumentException("视频不存在或无权限");
        cancel(videoId,owner); detach(videoId,owner);
    }

    @Transactional
    public void settle(long taskId) {
        var snapshot=tasks.selectById(taskId);
        if(snapshot==null || !TYPE.equals(snapshot.getBizType())) return;
        // 所有写入统一先锁业务记录，再锁任务，禁止旧任务在清空或更换之后发布。
        var video=videos.lockOwned(snapshot.getBizId(),snapshot.getCreateUser());
        var task=tasks.lockActive(taskId);
        if(task==null) return;
        if(video==null) { finish(task,"CANCELLED",null,null); return; }
        var source=sources.selectById(task.getExternalResourceId());
        if(source==null) { finish(task,"FAILED",null,"SOURCE_MISSING"); return; }
        if(source.getStorageObjectId()!=null) {
            String attachment=attach(task.getBizId(),video.getUserId(),source.getId(),source.getStorageObjectId());
            finish(task,"READY",attachment,null);
        } else if("FAILED".equals(source.getState()) && source.getAttempts()>=5)
            finish(task,"FAILED",null,source.getErrorCode());
    }

    private void finish(ImageImportTaskEntity task,String state,String result,String error) {
        tasks.update(null,new LambdaUpdateWrapper<ImageImportTaskEntity>().eq(ImageImportTaskEntity::getId,task.getId())
            .set(ImageImportTaskEntity::getState,state).set(ImageImportTaskEntity::getResultAttachmentId,result)
            .set(ImageImportTaskEntity::getErrorCode,error).set(ImageImportTaskEntity::getUpdateTime,LocalDateTime.now()));
    }
    private String attach(long videoId,long owner,long sourceId,long objectId) {
        storage.hold(objectId); detach(videoId,owner);
        var file=new AttachmentEntity(); file.fillCreateCommonField(owner);
        file.setStorageObjectId(objectId); file.setExternalResourceId(sourceId);
        file.setBizType(TYPE); file.setBizId(videoId); file.setIsPublic(0); files.insert(file);
        return file.getId();
    }
    private void detach(long videoId,long owner) {
        files.update(null,new LambdaUpdateWrapper<AttachmentEntity>().eq(AttachmentEntity::getBizType,TYPE)
            .eq(AttachmentEntity::getBizId,videoId).eq(AttachmentEntity::getUserId,owner)
            .set(AttachmentEntity::getIsDeleted,1).set(AttachmentEntity::getIsPublic,0)
            .set(AttachmentEntity::getUpdateUser,owner).set(AttachmentEntity::getUpdateTime,LocalDateTime.now()));
    }
    public Map<Long,String> coverFiles(long owner,Collection<Long> ids) {
        var result=new HashMap<Long,String>();
        if(!ids.isEmpty()) files.selectList(new LambdaQueryWrapper<AttachmentEntity>().eq(AttachmentEntity::getUserId,owner)
            .eq(AttachmentEntity::getBizType,TYPE).in(AttachmentEntity::getBizId,ids)).forEach(f->result.put(f.getBizId(),f.getFileKey()));
        return result;
    }
    public void fillCoverFiles(long owner,List<BVideoEntity> rows) {
        if(rows.isEmpty()) return;
        var ids=rows.stream().map(BVideoEntity::getId).toList();
        var attachments=files.selectList(new LambdaQueryWrapper<AttachmentEntity>().eq(AttachmentEntity::getUserId,owner)
            .eq(AttachmentEntity::getBizType,TYPE).in(AttachmentEntity::getBizId,ids));
        var fileByVideo=new HashMap<Long,AttachmentEntity>(); attachments.forEach(f->fileByVideo.put(f.getBizId(),f));
        var jobs=new HashMap<Long,ImageImportTaskEntity>(); tasks.latestFor(ids).forEach(j->jobs.put(j.getBizId(),j));
        var sourceIds=new HashSet<Long>();
        attachments.forEach(f->{if(f.getExternalResourceId()!=null) sourceIds.add(f.getExternalResourceId());});
        jobs.values().forEach(j->sourceIds.add(j.getExternalResourceId()));
        var sourceById=new HashMap<Long,ExternalResourceEntity>();
        if(!sourceIds.isEmpty()) sources.selectByIds(sourceIds).forEach(r->sourceById.put(r.getId(),r));
        for(var row:rows) {
            var file=fileByVideo.get(row.getId()); var job=jobs.get(row.getId());
            row.setCoverFileId(file==null?null:file.getFileKey());
            boolean pending=job!=null && ("PENDING".equals(job.getState()) || "RUNNING".equals(job.getState()));
            boolean failed=job!=null && "FAILED".equals(job.getState());
            row.setCoverState(pending?"PENDING":failed?"FAILED":file!=null?"READY":"NONE");
            Long sourceId=(pending||failed)?job.getExternalResourceId():file==null?null:file.getExternalResourceId();
            var source=sourceById.get(sourceId); row.setCover(source==null?null:source.getSourceUrl());
        }
    }
}
