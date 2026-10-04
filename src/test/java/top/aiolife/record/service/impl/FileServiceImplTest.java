package top.aiolife.record.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.lock.StorageObjectLock;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.enums.FileBizType;
import top.aiolife.record.mapper.IFileMapper;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.service.DoubanCoverUrlPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static top.aiolife.support.ImageFixtures.image;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

    @Mock
    private MinioUtil minioUtil;

    @Mock
    private MinioConfig minioConfig;

    @Mock
    private IFileMapper fileMapper;

    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileServiceImpl(minioUtil, minioConfig, mock(StorageObjectLock.class), new DoubanCoverUrlPolicy("doubanio.com"));
        ReflectionTestUtils.setField(fileService, "baseMapper", fileMapper);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void upload_使用MockMinio生成路径并保存公开头像() throws Exception {
        when(minioConfig.getBucketName()).thenReturn("test-bucket");
        MockMultipartFile file = new MockMultipartFile(
                "file", "Avatar.JPG", "image/jpeg", image("jpeg")
        );
        when(fileMapper.insert(any(FileEntity.class))).thenAnswer(invocation -> {
            FileEntity entity = invocation.getArgument(0);
            entity.setId("file-id");
            return 1;
        });
        when(minioUtil.getFilePreviewUrl("file-id")).thenReturn("https://preview/file-id");

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(42L);

            var result = fileService.upload(file, FileBizType.AVATAR);

            assertEquals("file-id", result.getId());
            assertEquals("https://preview/file-id", result.getFileUrl());

            ArgumentCaptor<String> objectNameCaptor = ArgumentCaptor.forClass(String.class);
            verify(minioUtil).putObject(eq("test-bucket"), objectNameCaptor.capture(), any(), eq(file.getSize()), any());
            assertTrue(objectNameCaptor.getValue().matches("42/avatar/[0-9a-f-]+\\.jpg"));

            ArgumentCaptor<FileEntity> entityCaptor = ArgumentCaptor.forClass(FileEntity.class);
            verify(fileMapper).insert(entityCaptor.capture());
            FileEntity saved = entityCaptor.getValue();
            assertEquals("avatar", saved.getBizType());
            assertEquals(1, saved.getIsPublic());
            assertEquals(objectNameCaptor.getValue(), saved.getFileName());

            completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
            verify(minioUtil, never()).removeObject(any(), any());
        }
    }

    @Test
    void upload_私有业务写入私有标记() throws Exception {
        when(minioConfig.getBucketName()).thenReturn("test-bucket");
        MockMultipartFile file = new MockMultipartFile(
                "file", "device.png", "image/png", image("png")
        );
        when(fileMapper.insert(any(FileEntity.class))).thenAnswer(invocation -> {
            FileEntity entity = invocation.getArgument(0);
            entity.setId("private-file-id");
            return 1;
        });

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);

            fileService.upload(file, FileBizType.DEVICE);

            ArgumentCaptor<FileEntity> entityCaptor = ArgumentCaptor.forClass(FileEntity.class);
            verify(fileMapper).insert(entityCaptor.capture());
            assertEquals(0, entityCaptor.getValue().getIsPublic());
            assertEquals("device", entityCaptor.getValue().getBizType());
        }
    }

    @Test
    void upload_数据库回滚时通过Mock删除Minio对象() throws Exception {
        when(minioConfig.getBucketName()).thenReturn("test-bucket");
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", image("png")
        );
        when(fileMapper.insert(any(FileEntity.class)))
                .thenThrow(new IllegalStateException("database error"));

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> fileService.upload(file, FileBizType.DEVICE)
            );
            assertTrue(exception.getMessage().startsWith("上传失败:"));

            ArgumentCaptor<String> objectNameCaptor = ArgumentCaptor.forClass(String.class);
            verify(minioUtil).putObject(eq("test-bucket"), objectNameCaptor.capture(), any(), eq(file.getSize()), any());

            completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
            verify(minioUtil).removeObject("test-bucket", objectNameCaptor.getValue());
        }
    }

    @Test
    void upload_空文件直接拒绝且不调用Minio() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0]
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> fileService.upload(emptyFile, FileBizType.DEVICE)
        );

        verify(minioUtil, never()).putObject(any(), any(), any(), anyLong(), any());
    }

    private void completeTransaction(int status) {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(status));
    }
}
