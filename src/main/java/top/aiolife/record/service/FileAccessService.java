package top.aiolife.record.service;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.aiolife.core.util.FileContentPolicy;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.enums.FileBizType;
import top.aiolife.record.pojo.vo.FileVO;
import top.aiolife.sso.service.SecondaryLockGuard;
import top.aiolife.system.mapper.StorageObjectMapper;

@Service
@RequiredArgsConstructor
public class FileAccessService {
    private final StorageObjectMapper storageObjects;
    private final IFileService fileService;
    private final MinioUtil minio;
    private final FilePreviewGuard guard;
    private final SecondaryLockGuard locks;
    public FileVO upload(MultipartFile file,String bizType,long userId) {
        if("bank_card_face".equals(bizType)) locks.checkMenus(userId,"/finance/bank-cards");
        return fileService.upload(file,FileBizType.fromBizType(bizType));
    }
    public void write(String id,boolean download,HttpServletResponse response) {
        response.setHeader("Cache-Control","private, no-store");
        var attachment=fileService.getByKey(id);
        if(attachment==null) { response.setStatus(404); return; }
        var decision=guard.check(attachment,guard.resolveLoginUserId());
        if(decision!=FilePreviewGuard.AccessDecision.ALLOW) {
            response.setStatus(decision==FilePreviewGuard.AccessDecision.UNAUTHORIZED?401:403); return;
        }
        var object=storageObjects.selectById(attachment.getStorageObjectId());
        if(object==null) { response.setStatus(404); return; }
        try(var input=minio.getFile(object.getBucket(),object.getObjectKey())) {
            response.setContentLengthLong(object.getFileSize());
            FileContentPolicy.writeResponse(input,response,attachment.getFileName(),download,object.getContentType());
        } catch(Exception e) { response.setStatus(404); }
    }
}
