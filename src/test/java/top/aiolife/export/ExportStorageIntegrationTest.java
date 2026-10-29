package top.aiolife.export;

import static org.junit.jupiter.api.Assertions.*;

import io.minio.BucketExistsArgs;
import io.minio.DeleteBucketPolicyArgs;
import io.minio.MinioClient;
import io.minio.RemoveBucketArgs;
import io.minio.SetBucketPolicyArgs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import top.aiolife.config.CbtiConfig;
import top.aiolife.config.MinioConfig;
import top.aiolife.export.config.ExportProperties;
import top.aiolife.export.service.ExportException;
import top.aiolife.export.storage.MinioExportArtifactStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** 只连接显式启用的本机临时 MinIO，使用公开的测试凭据。 */
@EnabledIfEnvironmentVariable(
        named = "AIO_EXPORT_TEST_MINIO_ENDPOINT",
        matches = "http://127\\.0\\.0\\.1:[0-9]+")
class ExportStorageIntegrationTest {
    @TempDir Path temporary;

    @Test
    void 私有对象上传读取清理且公开桶拒绝放行() throws Exception {
        var minio =
                MinioClient.builder()
                        .endpoint(System.getenv("AIO_EXPORT_TEST_MINIO_ENDPOINT"))
                        .credentials("export-fixture", "export-fixture-local-only")
                        .build();
        String bucket = "export-fixture-" + UUID.randomUUID();
        assertFalse(minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()));
        var properties = new ExportProperties();
        properties.setBucket(bucket);
        var common = new MinioConfig();
        common.setBucketName("public-business");
        var cbti = new CbtiConfig();
        cbti.setBucketName("public-cbti");
        var store = new MinioExportArtifactStore(minio, common, cbti, properties);
        Path file = temporary.resolve("data.xlsx");
        byte[] expected = "synthetic-private-fixture".getBytes();
        Files.write(file, expected);
        store.verifyPrivateStorage();
        assertTrue(minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()));
        store.put("exports/fixture/object", file, "application/octet-stream");
        try (var input = store.open("exports/fixture/object")) {
            assertArrayEquals(expected, input.readAllBytes());
        }
        assertTrue(
                store.scan(null).stream()
                        .anyMatch(item -> item.key().equals("exports/fixture/object")));
        try {
            minio.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(bucket)
                            .config(
                                    "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::"
                                            + bucket
                                            + "/*\"]}]}")
                            .build());
            assertThrows(ExportException.class, store::verifyPrivateStorage);
            assertThrows(ExportException.class, () -> store.open("exports/fixture/object"));
        } finally {
            minio.deleteBucketPolicy(DeleteBucketPolicyArgs.builder().bucket(bucket).build());
            store.delete("exports/fixture/object");
            assertTrue(store.scan(null).isEmpty());
            minio.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
        }
    }
}
