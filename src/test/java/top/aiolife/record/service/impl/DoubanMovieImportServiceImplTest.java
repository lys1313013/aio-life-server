package top.aiolife.record.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import top.aiolife.record.mapper.IMovieMapper;
import top.aiolife.record.pojo.entity.MovieEntity;
import top.aiolife.record.pojo.entity.UserBindEntity;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;
import top.aiolife.record.pojo.req.DoubanMovieImportItemReq;
import top.aiolife.record.pojo.req.DoubanMovieImportReq;
import top.aiolife.record.pojo.vo.DoubanMovieImportPreviewVO;
import top.aiolife.record.pojo.vo.DoubanMovieImportResultVO;
import top.aiolife.record.service.IUserBindService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DoubanMovieImportServiceImplTest {

    @Mock
    private IMovieMapper movieMapper;

    @Mock
    private IUserBindService userBindService;

    @Test
    void newRecordMapsMarkedDateAndLeavesBlankDateNull() {
        DoubanMovieImportServiceImpl service = new DoubanMovieImportServiceImpl(movieMapper, userBindService);
        when(userBindService.getBindByUserIdAndPlatform(7L, "douban"))
                .thenReturn(binding("doubanfilm"));
        when(movieMapper.selectList(any())).thenReturn(List.of());
        DoubanMovieImportReq request = request(item("1292052", LocalDate.of(2026, 9, 7), 5));

        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            DoubanMovieImportResultVO result = service.importRecords(request);
            assertEquals(1, result.getCreatedCount());
        }

        ArgumentCaptor<MovieEntity> captor = ArgumentCaptor.forClass(MovieEntity.class);
        verify(movieMapper).insert(captor.capture());
        MovieEntity inserted = captor.getValue();
        assertEquals(LocalDateTime.of(2026, 9, 7, 0, 0), inserted.getFinishTime());
        assertEquals(5, inserted.getRating());
        assertEquals("https://movie.douban.com/subject/1292052/", inserted.getUrl());

        reset(movieMapper);
        when(movieMapper.selectList(any())).thenReturn(List.of());
        request = request(item("1295644", null, null));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            service.importRecords(request);
        }
        verify(movieMapper).insert(captor.capture());
        assertNull(captor.getValue().getFinishTime(), "空 marked_date 不应自动填充当前时间");
    }

    @Test
    void duplicateAppearsInPreviewAndDefaultsToSkip() {
        DoubanMovieImportServiceImpl service = new DoubanMovieImportServiceImpl(movieMapper, userBindService);
        when(userBindService.getBindByUserIdAndPlatform(7L, "douban"))
                .thenReturn(binding("doubanfilm"));
        MovieEntity existing = existingMovie();
        when(movieMapper.selectList(any())).thenReturn(List.of(existing));
        DoubanMovieImportReq request = request(item("1292052", LocalDate.of(2026, 9, 7), 5));

        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            DoubanMovieImportPreviewVO preview = service.preview(request);
            assertEquals(1, preview.getDuplicateCount());
            assertEquals("42", preview.getDuplicates().get(0).getExistingId());

            DoubanMovieImportResultVO result = service.importRecords(request);
            assertEquals(1, result.getSkippedCount());
        }
        verify(movieMapper, never()).updateById((MovieEntity) any());
        verify(movieMapper, never()).insert((MovieEntity) any());
    }

    @Test
    void overwritePreservesBlankOptionalFieldsAndNonImportFields() {
        DoubanMovieImportServiceImpl service = new DoubanMovieImportServiceImpl(movieMapper, userBindService);
        when(userBindService.getBindByUserIdAndPlatform(7L, "douban"))
                .thenReturn(binding("doubanfilm"));
        MovieEntity existing = existingMovie();
        existing.setDirector("原导演");
        existing.setRemark("原备注");
        existing.setFileId("cover-id");
        existing.setStartTime(LocalDateTime.of(2020, 1, 1, 0, 0));
        when(movieMapper.selectList(any())).thenReturn(List.of(existing));
        DoubanMovieImportReq request = request(item("1292052", null, null));
        request.setDuplicatePolicy("overwrite");

        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            DoubanMovieImportResultVO result = service.importRecords(request);
            assertEquals(1, result.getUpdatedCount());
        }
        assertEquals("原导演", existing.getDirector());
        assertEquals("原备注", existing.getRemark());
        assertEquals("cover-id", existing.getFileId());
        assertEquals(LocalDateTime.of(2020, 1, 1, 0, 0), existing.getStartTime());
        verify(movieMapper).updateById(existing);
    }

    private static DoubanMovieImportReq request(DoubanMovieImportItemReq item) {
        DoubanMovieImportReq request = new DoubanMovieImportReq();
        request.setFormat("aio-life-movie-import");
        request.setVersion(1);
        request.setSource("douban");
        request.setDoubanUserId("doubanfilm");
        request.setRecords(List.of(item));
        return request;
    }

    private static DoubanMovieImportItemReq item(String subjectId, LocalDate markedDate, Integer rating) {
        DoubanMovieImportItemReq item = new DoubanMovieImportItemReq();
        item.setRowNumber(2);
        item.setDoubanSubjectId(subjectId);
        item.setTitle("导入片名");
        item.setType("movie");
        item.setUrl("http://movie.douban.com/subject/" + subjectId + "/?from=mine");
        item.setStatus(ProgressStatusEnum.COMPLETED);
        item.setMarkedDate(markedDate);
        item.setRating(rating);
        return item;
    }

    private static UserBindEntity binding(String username) {
        UserBindEntity binding = new UserBindEntity();
        binding.setPlatformUsername(username);
        return binding;
    }

    private static MovieEntity existingMovie() {
        MovieEntity entity = new MovieEntity();
        entity.setId(42L);
        entity.setUserId(7L);
        entity.setTitle("已有片名");
        entity.setUrl("https://movie.douban.com/subject/1292052/");
        return entity;
    }
}
