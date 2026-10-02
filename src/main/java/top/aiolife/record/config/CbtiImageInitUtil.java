package top.aiolife.record.config;

import top.aiolife.core.lock.StorageObjectLock;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.aiolife.config.CbtiConfig;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.mapper.ICbtiPersonalityMapper;
import top.aiolife.record.pojo.entity.CbtiPersonalityEntity;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CBTI 图片初始化工具类（原本在启动时运行，现改为手动调用）
 *
 * @author Ethan
 * @date 2026/04/18
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CbtiImageInitUtil {

    private final CbtiConfig cbtiConfig;

    private final MinioConfig minioConfig;

    private final MinioUtil minioUtil;

    private final ICbtiPersonalityMapper cbtiPersonalityMapper;
    private final StorageObjectLock objectLock;

    /**
     * 手动触发初始化图片上传
     */
    public void initImages() {
        if (!cbtiConfig.isEnabled()) {
            log.warn("CBTI 未启用，跳过初始化");
            return;
        }

        LambdaQueryWrapper<CbtiPersonalityEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CbtiPersonalityEntity::getIsDeleted, 0);
        List<CbtiPersonalityEntity> list = cbtiPersonalityMapper.selectList(wrapper);
        
        if (list.isEmpty()) {
            log.warn("CBTI 初始化跳过：数据库未查询到人格数据");
            return;
        }

        String bucketName = resolveBucketName();

        for (CbtiPersonalityEntity entity : list) {
            uploadCharacterIfPresent(bucketName, entity.getImageObject());
        }

        log.info("CBTI 初始化完成：共处理 {} 条人格图片上传检查", list.size());
    }

    private void uploadCharacterIfPresent(String bucketName, String imageObject) {
        if (!StringUtils.hasText(bucketName) || !StringUtils.hasText(imageObject)) {
            return;
        }
        if (!StringUtils.hasText(cbtiConfig.getCharactersDir())) {
            return;
        }

        Path dir = Path.of(cbtiConfig.getCharactersDir());
        Path filePath = dir.resolve(Path.of(imageObject).getFileName().toString());
        if (!Files.exists(filePath)) {
            return;
        }
        try (var lease = objectLock.acquire(bucketName, imageObject);
             InputStream inputStream = Files.newInputStream(filePath)) {
            if (minioUtil.objectExists(bucketName, imageObject)) return;
            long size = Files.size(filePath);
            minioUtil.putObject(bucketName, imageObject, inputStream, size, "image/png");
            log.info("CBTI 图片上传成功: {}/{}", bucketName, imageObject);
        } catch (Exception e) {
            log.warn("CBTI 图片上传失败: bucket={}, object={}", bucketName, imageObject, e);
        }
    }

    private String resolveBucketName() {
        if (StringUtils.hasText(cbtiConfig.getBucketName())) {
            return cbtiConfig.getBucketName();
        }
        if (minioConfig != null && StringUtils.hasText(minioConfig.getBucketName())) {
            return minioConfig.getBucketName();
        }
        return "aiolife";
    }
}
