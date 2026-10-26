package top.aiolife.record.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.config.CbtiConfig;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.util.FileContentPolicy;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.mapper.ICbtiPersonalityMapper;
import top.aiolife.record.pojo.entity.CbtiPersonalityEntity;

/** CBTI 公开人格图片只读取当前人格绑定的对象，不接受客户端指定存储桶。 */
@RestController
@RequiredArgsConstructor
public class CbtiImageController {
    public static final String PATH = "/cbti/images/**";
    private final MinioUtil minio;
    private final MinioConfig minioConfig;
    private final CbtiConfig cbtiConfig;
    private final ICbtiPersonalityMapper personalities;

    @GetMapping("/cbti/images/{*objectName}")
    public void image(@PathVariable String objectName, HttpServletResponse response) {
        String key = objectName.startsWith("/") ? objectName.substring(1) : objectName;
        if (!StringUtils.hasText(key) || key.contains("..") || key.contains("\\")
                || personalities.selectCount(new LambdaQueryWrapper<CbtiPersonalityEntity>()
                    .eq(CbtiPersonalityEntity::getImageObject, key)) == 0) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String bucket = StringUtils.hasText(cbtiConfig.getBucketName()) ? cbtiConfig.getBucketName()
                : StringUtils.hasText(minioConfig.getBucketName()) ? minioConfig.getBucketName() : "aiolife";
        try (var input = minio.getFile(bucket, key)) {
            FileContentPolicy.writeResponse(input, response, key, false);
        } catch (Exception exception) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
