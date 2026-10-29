package top.aiolife.export;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.minio.BucketExistsArgs;
import io.minio.GetBucketPolicyArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import top.aiolife.config.CbtiConfig;
import top.aiolife.config.MinioConfig;
import top.aiolife.export.config.ExportProperties;
import top.aiolife.export.service.ExportException;
import top.aiolife.export.storage.MinioExportArtifactStore;

class MinioExportArtifactStoreTest {
    private final MinioClient minio = mock(MinioClient.class);
    private final ExportProperties properties = new ExportProperties();
    private MinioExportArtifactStore store;

    @BeforeEach
    void setup() {
        var common = new MinioConfig();
        common.setBucketName("public-business");
        var cbti = new CbtiConfig();
        cbti.setBucketName("public-cbti");
        store = new MinioExportArtifactStore(minio, common, cbti, properties);
    }

    @Test
    void 缺失桶自动创建后校验私有策略() throws Exception {
        var noPolicy = storageError("NoSuchBucketPolicy");
        when(minio.getBucketPolicy(any(GetBucketPolicyArgs.class))).thenThrow(noPolicy);
        store.verifyPrivateStorage();
        var order = inOrder(minio);
        order.verify(minio).bucketExists(any(BucketExistsArgs.class));
        order.verify(minio).makeBucket(argThat(args -> properties.getBucket().equals(args.bucket())));
        order.verify(minio).getBucketPolicy(any(GetBucketPolicyArgs.class));
    }

    @Test
    void 已有私有桶无需创建() throws Exception {
        when(minio.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minio.getBucketPolicy(any(GetBucketPolicyArgs.class))).thenReturn("");
        store.verifyPrivateStorage();
        verify(minio, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"public-business", "public-cbti"})
    void 禁止复用业务桶且不操作存储(String bucket) {
        properties.setBucket(bucket);
        assertThrows(ExportException.class, store::verifyPrivateStorage);
        verifyNoInteractions(minio);
    }

    @Test
    void 并发建桶已属于当前身份时仍校验策略() throws Exception {
        doThrow(storageError("BucketAlreadyOwnedByYou"))
                .when(minio).makeBucket(any(MakeBucketArgs.class));
        when(minio.getBucketPolicy(any(GetBucketPolicyArgs.class))).thenReturn("policy");
        assertThrows(ExportException.class, store::verifyPrivateStorage);
        verify(minio).getBucketPolicy(any(GetBucketPolicyArgs.class));
    }

    @Test
    void 并发建桶已属于当前身份且私有时可继续() throws Exception {
        doThrow(storageError("BucketAlreadyOwnedByYou"))
                .when(minio).makeBucket(any(MakeBucketArgs.class));
        var noPolicy = storageError("NoSuchBucketPolicy");
        when(minio.getBucketPolicy(any(GetBucketPolicyArgs.class))).thenThrow(noPolicy);
        store.verifyPrivateStorage();
    }

    @ParameterizedTest
    @ValueSource(strings = {"AccessDenied", "BucketAlreadyExists"})
    void 建桶失败不能继续(String code) throws Exception {
        var error = storageError(code);
        doThrow(error).when(minio).makeBucket(any(MakeBucketArgs.class));
        assertSame(error, assertThrows(ErrorResponseException.class, store::verifyPrivateStorage));
        verify(minio, never()).getBucketPolicy(any(GetBucketPolicyArgs.class));
    }

    @Test
    void 已有桶附带策略时拒绝访问() throws Exception {
        when(minio.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minio.getBucketPolicy(any(GetBucketPolicyArgs.class))).thenReturn("policy");
        assertThrows(ExportException.class, store::verifyPrivateStorage);
        verify(minio, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void 无权检查策略不能放行() throws Exception {
        when(minio.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        var error = storageError("AccessDenied");
        when(minio.getBucketPolicy(any(GetBucketPolicyArgs.class))).thenThrow(error);
        assertSame(error, assertThrows(ErrorResponseException.class, store::verifyPrivateStorage));
    }

    private ErrorResponseException storageError(String code) {
        var response = mock(ErrorResponse.class);
        when(response.code()).thenReturn(code);
        return new ErrorResponseException(response, null, "fixture");
    }
}
