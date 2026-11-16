package top.aiolife.record.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.aiolife.core.util.FileContentPolicy;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.mapper.PublicImageMapper;

/** 公开内容地址每次协商验证权限；私有附件不进入此入口。 */
@Service
@RequiredArgsConstructor
public class PublicImageService {
    private final PublicImageMapper images;
    private final MinioUtil minio;
    public void write(String hash,String extension,HttpServletRequest request,HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control","no-store");
        response.setHeader("X-Content-Type-Options","nosniff");
        response.setHeader("Access-Control-Allow-Origin","*");
        response.setHeader("Content-Security-Policy","default-src 'none'; sandbox");
        var object=images.selectPublished(hash);
        var types=Map.of("png","image/png","jpg","image/jpeg","gif","image/gif","webp","image/webp","bmp","image/bmp");
        if(object==null || !java.util.Objects.equals(types.get(extension),object.getContentType())) { response.setStatus(404); return; }
        String etag="\""+object.getContentHash()+"\"";
        response.setHeader("ETag",etag);
        response.setHeader("Cache-Control","public, no-cache");
        response.setContentType(object.getContentType());
        if(etag.equals(request.getHeader("If-None-Match"))) { response.setStatus(304); return; }
        try(var input=minio.getFile(object.getBucket(),object.getObjectKey())) {
            response.setContentLengthLong(object.getFileSize());
            FileContentPolicy.writeResponse(input,response,"image."+extension,false,object.getContentType());
        } catch(Exception e) { response.setStatus(404); response.setHeader("Cache-Control","no-store"); }
    }
}
