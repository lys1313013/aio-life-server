package top.aiolife.record.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import top.aiolife.core.util.FileContentPolicy;
import top.aiolife.core.id.AttachmentKey;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.enums.AttachmentBizType;
import top.aiolife.record.mapper.IAttachmentMapper;
import top.aiolife.record.pojo.entity.AttachmentEntity;
import top.aiolife.record.pojo.vo.AttachmentVO;
import top.aiolife.record.service.ExternalResourceService;
import top.aiolife.record.service.IAttachmentService;
import top.aiolife.record.service.VideoCoverStorage;
import top.aiolife.system.mapper.StorageObjectMapper;

@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl extends ServiceImpl<IAttachmentMapper,AttachmentEntity> implements IAttachmentService {
    private final MinioUtil minio;
    private final VideoCoverStorage storage;
    private final StorageObjectMapper objects;
    private final ExternalResourceService sources;

    @Override
    public AttachmentEntity getByKey(String fileKey) {
        AttachmentKey.require(fileKey);
        return getOne(new LambdaQueryWrapper<AttachmentEntity>().eq(AttachmentEntity::getFileKey, fileKey));
    }

    @Override
    @Transactional(rollbackFor=Exception.class)
    public AttachmentVO upload(MultipartFile file,AttachmentBizType bizType) {
        if(file==null || file.isEmpty() || file.getSize()>FileContentPolicy.MAX_IMAGE_BYTES)
            throw new IllegalArgumentException("文件必须为1字节至10MB");
        byte[] bytes;
        try(var input=file.getInputStream()) { bytes=input.readNBytes(FileContentPolicy.MAX_IMAGE_BYTES+1); }
        catch(IOException e) { throw new IllegalArgumentException("文件无法读取",e); }
        boolean template=bizType==AttachmentBizType.BANK_CARD_TEMPLATE_COVER;
        if(bizType==AttachmentBizType.CBTI_PERSONALITY)
            throw new IllegalArgumentException("CBTI 人格图片只能通过 CBTI 管理上传接口提交");
        if(template) StpUtil.checkRole("admin");
        var image=switch(bizType) {
            case AVATAR,WARDROBE_ITEM,MOVIE,READ_RECORD,DEVICE,BANK_CARD_COVER,BANK_CARD_TEMPLATE_COVER -> FileContentPolicy.requireImage(bytes);
            default -> FileContentPolicy.detectImage(bytes);
        };
        if(template || bizType==AttachmentBizType.BANK_CARD_COVER) {
            if(bytes.length>5*1024*1024 || image==null || !Set.of("image/png","image/jpeg").contains(image.contentType()))
                throw new IllegalArgumentException("卡面仅支持5MB以内的PNG或JPEG图片");
        }
        if(template) try(var input=new ByteArrayInputStream(bytes)) {
            var output=new ByteArrayOutputStream();
            ImageIO.write(ImageIO.read(input),"png",output);
            bytes=output.toByteArray();
            if(bytes.length>5*1024*1024) throw new IllegalArgumentException("处理后的卡面不能超过5MB");
            image=new FileContentPolicy.ImageType("image/png","png",image.width(),image.height());
        } catch(IOException e) { throw new IllegalArgumentException("卡面处理失败",e); }
        long owner=StpUtil.getLoginIdAsLong();
        var object=storage.store(bytes,owner,image);
        var attachment=new AttachmentEntity(); attachment.fillCreateCommonField(owner);
        attachment.setStorageObjectId(object.getId()); attachment.setFileName(file.getOriginalFilename());
        attachment.setBizType(bizType.getBizType());
        // 头像保存绑定之前也不公开；模板归属为系统，但创建审计仍记录管理员。
        attachment.setIsPublic(0);
        if(template) attachment.setUserId(null);
        save(attachment);
        return toVO(attachment);
    }

    @Override
    @Transactional(rollbackFor=Exception.class)
    public AttachmentVO uploadFromUrl(String url,AttachmentBizType type) {
        if(type!=AttachmentBizType.MOVIE && type!=AttachmentBizType.READ_RECORD) throw new IllegalArgumentException("该业务请使用图片上传");
        long owner=StpUtil.getLoginIdAsLong();
        var source=sources.image(url,type,owner);
        var object=storage.hold(source.getStorageObjectId());
        var attachment=new AttachmentEntity(); attachment.fillCreateCommonField(owner);
        attachment.setStorageObjectId(source.getStorageObjectId()); attachment.setExternalResourceId(source.getId());
        attachment.setBizType(type.getBizType()); attachment.setIsPublic(0);
        attachment.setFileName("cover."+object.getObjectKey().substring(object.getObjectKey().lastIndexOf('.')+1));
        save(attachment);
        return toVO(attachment);
    }

    @Override
    @Transactional(rollbackFor=Exception.class)
    public void bindBizId(List<String> ids,String type,Long bizId) {
        if (ids != null) ids.forEach(AttachmentKey::require);
        if(ids==null || ids.isEmpty()) return;
        if(bizId==null) throw new IllegalArgumentException("绑定业务ID不能为空");
        if(!Set.of("feedback","feedback_comment","honor_record","event_record").contains(type))
            throw new IllegalArgumentException("该附件必须通过对应业务入口绑定");
        long owner=StpUtil.getLoginIdAsLong();
        if(baseMapper.lockBusiness(type,bizId,owner)==null) throw new IllegalArgumentException("业务记录不存在或无权限");
        for(int i=0;i<ids.size();i++) {
            var attachment=baseMapper.selectForBindingByKey(ids.get(i));
            if(attachment==null || !Long.valueOf(owner).equals(attachment.getUserId()) || !type.equals(attachment.getBizType())
                    || (attachment.getBizId()!=null && !attachment.getBizId().equals(bizId)))
                throw new IllegalArgumentException("附件不存在、已绑定其他业务或无权限");
            storage.hold(attachment.getStorageObjectId());
            update(null,new LambdaUpdateWrapper<AttachmentEntity>().eq(AttachmentEntity::getId,attachment.getId())
                .set(AttachmentEntity::getBizId,bizId).set(AttachmentEntity::getSortOrder,i)
                .set(AttachmentEntity::getUpdateUser,owner).set(AttachmentEntity::getUpdateTime,LocalDateTime.now()));
        }
    }

    @Override
    public List<AttachmentVO> getByBiz(String type,Long id) {
        return list(new LambdaQueryWrapper<AttachmentEntity>().eq(AttachmentEntity::getBizType,type).eq(AttachmentEntity::getBizId,id)
            .orderByAsc(AttachmentEntity::getSortOrder,AttachmentEntity::getId)).stream().map(this::toVO).toList();
    }

    @Override
    public AttachmentVO toVO(AttachmentEntity attachment) {
        if(attachment==null) return null;
        var vo=new AttachmentVO(); BeanUtils.copyProperties(attachment,vo);
        vo.setId(attachment.getFileKey());
        var object=objects.selectById(attachment.getStorageObjectId());
        if(object==null) throw new IllegalStateException("附件对象不存在");
        vo.setFileSize(object.getFileSize()); vo.setFileType(object.getContentType());
        vo.setFileUrl(minio.getFilePreviewUrl(attachment.getFileKey()));
        return vo;
    }
}
