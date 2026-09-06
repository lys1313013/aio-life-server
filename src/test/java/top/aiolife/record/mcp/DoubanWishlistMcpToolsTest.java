package top.aiolife.record.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.aiolife.record.mcp.req.DoubanWishlistAddMcpReq;
import top.aiolife.record.mcp.vo.DoubanWishlistAddMcpVO;
import top.aiolife.record.pojo.entity.MovieEntity;
import top.aiolife.record.pojo.entity.ReadRecordEntity;
import top.aiolife.record.pojo.req.MovieReq;
import top.aiolife.record.pojo.req.ReadRecordReq;
import top.aiolife.record.service.IMovieService;
import top.aiolife.record.service.IReadRecordService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoubanWishlistMcpToolsTest {

    @Mock
    private IMovieService movieService;

    @Mock
    private IReadRecordService readRecordService;

    @InjectMocks
    private DoubanWishlistMcpTools tools;

    @Test
    void add_新电影解析后以想看状态保存() {
        DoubanWishlistAddMcpReq req = request("https://movie.douban.com/subject/36354085/?from=subject-page");
        MovieReq parsed = new MovieReq();
        parsed.setTitle("测试电影");
        parsed.setFileId("movie-cover-file-id");
        parsed.setCurrentProgress(null);
        when(movieService.parseDouban("https://movie.douban.com/subject/36354085/")).thenReturn(parsed);
        when(movieService.saveRecord(any(MovieReq.class))).thenReturn(1001L);

        DoubanWishlistAddMcpVO result = tools.douban_wishlist_add(req);

        assertTrue(result.isCreated());
        assertEquals("movie", result.getMediaType());
        assertEquals(1001L, result.getId());
        assertEquals("测试电影", result.getTitle());
        assertEquals("想看", result.getStatus());
        assertEquals("movie-cover-file-id", result.getFileId());

        ArgumentCaptor<MovieReq> captor = ArgumentCaptor.forClass(MovieReq.class);
        verify(movieService).saveRecord(captor.capture());
        assertEquals("https://movie.douban.com/subject/36354085/", captor.getValue().getUrl());
        assertEquals(0, captor.getValue().getStatus());
        assertEquals(0, captor.getValue().getCurrentProgress());
        verify(readRecordService, never()).parseDouban(any());
    }

    @Test
    void add_同一电影已存在时直接返回且不再解析保存() {
        MovieEntity existing = new MovieEntity();
        existing.setId(1002L);
        existing.setTitle("已有电影");
        existing.setStatus(2);
        existing.setFileId("existing-movie-cover");
        when(movieService.findByDoubanSubjectId("36354085")).thenReturn(existing);

        DoubanWishlistAddMcpVO result = tools.douban_wishlist_add(
                request("https://movie.douban.com/subject/36354085/"));

        assertFalse(result.isCreated());
        assertEquals(1002L, result.getId());
        assertEquals("看过", result.getStatus());
        assertEquals("existing-movie-cover", result.getFileId());
        verify(movieService, never()).parseDouban(any());
        verify(movieService, never()).saveRecord(any());
    }

    @Test
    void add_新书籍解析后以想读状态保存() {
        ReadRecordReq parsed = new ReadRecordReq();
        parsed.setTitle("测试书籍");
        parsed.setFileId("book-cover-file-id");
        when(readRecordService.parseDouban("https://book.douban.com/subject/4913064/")).thenReturn(parsed);
        when(readRecordService.saveRecord(any(ReadRecordReq.class))).thenReturn(2001L);

        DoubanWishlistAddMcpVO result = tools.douban_wishlist_add(
                request("https://book.douban.com/subject/4913064/"));

        assertTrue(result.isCreated());
        assertEquals("book", result.getMediaType());
        assertEquals(2001L, result.getId());
        assertEquals("测试书籍", result.getTitle());
        assertEquals("想读", result.getStatus());
        assertEquals("book-cover-file-id", result.getFileId());

        ArgumentCaptor<ReadRecordReq> captor = ArgumentCaptor.forClass(ReadRecordReq.class);
        verify(readRecordService).saveRecord(captor.capture());
        assertEquals("https://book.douban.com/subject/4913064/", captor.getValue().getUrl());
        assertEquals(0, captor.getValue().getStatus());
        assertEquals(0, captor.getValue().getCurrentProgress());
        verify(movieService, never()).parseDouban(any());
    }

    @Test
    void add_同一书籍已存在时直接返回且不再解析保存() {
        ReadRecordEntity existing = new ReadRecordEntity();
        existing.setId(2002L);
        existing.setTitle("已有书籍");
        existing.setStatus(1);
        existing.setFileId("existing-book-cover");
        when(readRecordService.findByDoubanSubjectId("4913064")).thenReturn(existing);

        DoubanWishlistAddMcpVO result = tools.douban_wishlist_add(
                request("https://book.douban.com/subject/4913064/"));

        assertFalse(result.isCreated());
        assertEquals(2002L, result.getId());
        assertEquals("阅读中", result.getStatus());
        assertEquals("existing-book-cover", result.getFileId());
        verify(readRecordService, never()).parseDouban(any());
        verify(readRecordService, never()).saveRecord(any());
    }

    @Test
    void add_非法地址在访问业务服务前被拒绝() {
        assertThrows(IllegalArgumentException.class,
                () -> tools.douban_wishlist_add(request("https://example.com/subject/36354085/")));
        verify(movieService, never()).findByDoubanSubjectId(any());
        verify(readRecordService, never()).findByDoubanSubjectId(any());
    }

    @Test
    void add_已有电影缺少封面时补封面但不新增记录() {
        MovieEntity existing = new MovieEntity();
        existing.setId(1003L);
        existing.setTitle("待补封面电影");
        existing.setStatus(0);
        when(movieService.findByDoubanSubjectId("36354085")).thenReturn(existing);
        MovieReq parsed = new MovieReq();
        parsed.setFileId("repaired-cover-file-id");
        when(movieService.parseDouban("https://movie.douban.com/subject/36354085/")).thenReturn(parsed);

        DoubanWishlistAddMcpVO result = tools.douban_wishlist_add(
                request("https://movie.douban.com/subject/36354085/"));

        assertFalse(result.isCreated());
        assertEquals("repaired-cover-file-id", result.getFileId());
        verify(movieService).updateCoverFileId(1003L, "repaired-cover-file-id");
        verify(movieService, never()).saveRecord(any());
    }

    @Test
    void add_封面上传失败时不保存无图记录() {
        MovieReq parsed = new MovieReq();
        parsed.setTitle("无封面电影");
        when(movieService.parseDouban("https://movie.douban.com/subject/36354085/")).thenReturn(parsed);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> tools.douban_wishlist_add(request("https://movie.douban.com/subject/36354085/")));

        assertTrue(exception.getMessage().contains("封面上传失败"));
        verify(movieService, never()).saveRecord(any());
    }

    private DoubanWishlistAddMcpReq request(String url) {
        DoubanWishlistAddMcpReq req = new DoubanWishlistAddMcpReq();
        req.setUrl(url);
        return req;
    }
}
