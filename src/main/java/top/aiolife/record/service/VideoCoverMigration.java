package top.aiolife.record.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import top.aiolife.config.*;
import top.aiolife.core.lock.StorageObjectLock;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.mapper.*;
import top.aiolife.record.pojo.entity.*;
import top.aiolife.system.mapper.StorageObjectMapper;

/** 显式离线运维入口。默认只读，--apply 才排队导入；不自动变更数据库结构。 */
public final class VideoCoverMigration {
    private VideoCoverMigration() {}
    @Configuration
    @org.springframework.context.annotation.Profile("video-cover-migration")
    @EnableAutoConfiguration
    @Import({MybatisPlusConfig.class, MinioConfig.class, RedissonConfig.class, MinioUtil.class,
            StorageObjectLock.class, VideoCoverDownloader.class, VideoCoverStorage.class,
            VideoCoverService.class, VideoCoverWorker.class})
    static class MigrationConfiguration {}

    public static void main(String[] args) throws Exception {
        boolean apply = Arrays.asList(args).contains("--apply");
        String reportPath = Arrays.stream(args).filter(a -> a.startsWith("--report="))
                .map(a -> a.substring(9)).findFirst().orElse("artifacts/video-cover-migration/report.json");
        var app = new SpringApplication(MigrationConfiguration.class);
        app.setAdditionalProfiles("video-cover-migration");
        app.setWebApplicationType(WebApplicationType.NONE);
        // 不启动 Web、不触发业务调度或图片初始化；仅导入明确列出的服务。
        try (var context = app.run("--spring.mail.test-connection=false", "--aio.life.scheduling.enabled=false",
                "--spring.main.banner-mode=off", "--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration,org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration,org.springframework.boot.autoconfigure.data.neo4j.Neo4jRepositoriesAutoConfiguration")) {
            var videos = context.getBean(IBVideoMapper.class);
            var service = context.getBean(VideoCoverService.class);
            var worker = context.getBean(VideoCoverWorker.class);
            var rows = videos.selectList(new LambdaQueryWrapper<BVideoEntity>()
                    .isNotNull(BVideoEntity::getCover).ne(BVideoEntity::getCover, ""));
            Map<String,Object> report = new LinkedHashMap<>();
            report.put("startedAt", Instant.now().toString()); report.put("apply", apply);
            report.put("activeWithCover", rows.size()); report.put("bucket", "system"); report.put("prefix", "bvedio/");
            List<String> rejected = new ArrayList<>();
            int queued = 0;
            if (apply) {
                for (var video : rows) {
                    if (video.getCoverFileId() != null && "READY".equals(video.getCoverState())) continue;
                    if ("PENDING".equals(video.getCoverState())) continue;
                    try { service.enqueue(video.getId(), video.getUserId(), video.getCover(), true); queued++; }
                    catch (IllegalArgumentException e) { rejected.add(String.valueOf(video.getId())); }
                }
                long deadline = System.currentTimeMillis() + 20 * 60_000;
                while (System.currentTimeMillis() < deadline) {
                    worker.runBatch();
                    long pending = videos.selectCount(new LambdaQueryWrapper<BVideoEntity>().eq(BVideoEntity::getCoverState,"PENDING"));
                    if (pending == 0) break;
                    Thread.sleep(1000);
                }
            }
            report.put("queued", queued); report.put("rejectedVideoIds", rejected);
            var current = videos.selectList(new LambdaQueryWrapper<BVideoEntity>()
                    .isNotNull(BVideoEntity::getCover).ne(BVideoEntity::getCover, ""));
            Map<String,Long> states = new TreeMap<>();
            for (var video : current) states.merge(video.getCoverState(),1L,Long::sum);
            report.put("states", states);
            if (apply) {
                Set<Long> verified = new HashSet<>(); List<String> invalid = new ArrayList<>();
                long logicalBytes=0, storedBytes=0;
                var objects = context.getBean(StorageObjectMapper.class);
                var files = context.getBean(IFileMapper.class);
                var minio = context.getBean(MinioUtil.class);
                for (var video : current) {
                    if (!"READY".equals(video.getCoverState()) || video.getCoverFileId()==null) { invalid.add(String.valueOf(video.getId())); continue; }
                    var file = files.selectById(video.getCoverFileId());
                    if (file==null || !Objects.equals(file.getCreateUser(),video.getUserId()) || !Objects.equals(file.getBizId(),video.getId())) { invalid.add(String.valueOf(video.getId())); continue; }
                    var object = objects.selectById(file.getStorageObjectId());
                    if (object==null) { invalid.add(String.valueOf(video.getId())); continue; }
                    logicalBytes+=object.getFileSize();
                    if (!verified.add(object.getId())) continue;
                    try (InputStream stream=minio.getFile(object.getBucket(),object.getObjectKey())) {
                        byte[] bytes=stream.readNBytes(top.aiolife.core.util.FileContentPolicy.MAX_IMAGE_BYTES+1);
                        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                        if (!hash.equals(object.getSha256()) || bytes.length!=object.getFileSize()) throw new IllegalStateException("OBJECT_HASH_MISMATCH");
                        top.aiolife.core.util.FileContentPolicy.requireImage(bytes); storedBytes+=bytes.length;
                    }
                }
                report.put("verifiedObjects",verified.size()); report.put("logicalBytes",logicalBytes);
                report.put("storedBytes",storedBytes); report.put("invalidVideoIds",invalid);
                report.put("success",invalid.isEmpty() && rejected.isEmpty());
            }
            report.put("finishedAt",Instant.now().toString());
            var output=Path.of(reportPath); Files.createDirectories(output.toAbsolutePath().getParent());
            new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(),report);
            System.out.println("VIDEO_COVER_MIGRATION " + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(report));
            if (apply && !Boolean.TRUE.equals(report.get("success"))) throw new IllegalStateException("迁移未全部成功，请检查报告后重跑");
        }
    }
}
