package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.List;
import top.aiolife.record.pojo.vo.ReadRecordVO;
import top.aiolife.record.pojo.req.MovieReq;
import org.jsoup.Jsoup;
import top.aiolife.record.util.DoubanSubjectUrl;
import top.aiolife.record.service.VideoCoverDownloader;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.verifyNoInteractions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import top.aiolife.record.api.BaseIntegrationTest;
import top.aiolife.record.mapper.CreativeWorkMapper;
import top.aiolife.record.mapper.IMovieMapper;
import top.aiolife.record.pojo.entity.MovieEntity;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;
import top.aiolife.record.pojo.query.MovieQuery;
import top.aiolife.record.pojo.query.ReadRecordQuery;
import top.aiolife.record.service.CreativeWorkService;
import top.aiolife.record.service.IMovieService;
import top.aiolife.record.service.IReadRecordService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class RankedWorkIntegrationTest extends BaseIntegrationTest {
    @Autowired IMovieService movies;
    @Autowired IReadRecordService books;
    @Autowired CreativeWorkMapper works;
    @Autowired IMovieMapper movieMapper;
    @Autowired CreativeWorkService catalog;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean VideoCoverDownloader downloader;

    @Test
    void 共享封面按图片身份读取本地对象() {
        var book = works.findSubject("book_subject", "1007305");
        book.setCoverUrl("https://img1.doubanio.com/view/subject/l/public/s9900112233.jpg");
        works.updateById(book);
        jdbc.update("""
            INSERT INTO storage_object(id,bucket,object_key,content_hash,file_size,content_type)
            VALUES (99112233,'fixture','ranking-cover.jpg',repeat('a',64),4,'image/jpeg')
            """);
        jdbc.update("""
            INSERT INTO external_resource(id,platform,resource_type,external_key,variant,source_url,state,storage_object_id)
            VALUES (99112233,'douban','book_cover','s9900112233','l',?,'READY',99112233)
            """, "https://img2.doubanio.com/view/subject/l/public/s9900112233.jpg");
        var record = new ReadRecordVO();
        record.setPlatform("douban");
        record.setExternalKey("1007305");
        catalog.fill(DoubanSubjectUrl.MediaType.BOOK, List.of(record));
        assertEquals("/creative-works/" + book.getId() + "/cover", record.getCoverImgUrl());
        assertEquals(record.getCoverImgUrl(), catalog.cachedCoverPath(book));
        assertNull(record.getRetryCoverWorkId());
        verifyNoInteractions(downloader);
    }

    @Test
    void 更换历史记录链接直接改变榜单且普通保存不创建作品() {
        jdbc.update("INSERT INTO sys_user(id,username,nickname) VALUES (?,?,?) ON CONFLICT(id) DO NOTHING",
                TEST_USER_ID, "ranking-test-fixture", "榜单测试");
        var req = new MovieReq();
        req.setTitle("个人片名");
        req.setType(1);
        req.setStatus(ProgressStatusEnum.NOT_STARTED);
        req.setUrl("https://movie.douban.com/subject/90000123455/");
        var id = movies.saveRecord(req);
        var filter = new MovieQuery();
        filter.setRankingListId(24001L);
        assertEquals("douban", movieMapper.selectById(id).getPlatform());
        assertEquals("90000123455", movieMapper.selectById(id).getExternalKey());
        assertNull(works.findSubject("movie_subject", "90000123455"));
        assertTrue(movies.getVOById(id).getRankings().isEmpty());

        movieMapper.update(new LambdaUpdateWrapper<MovieEntity>().eq(MovieEntity::getId, id)
                .set(MovieEntity::getUrl, "https://movie.douban.com/subject/1300267?from=archive"));
        assertEquals("1300267", movieMapper.selectById(id).getExternalKey());
        assertEquals(34, movies.getVOById(id).getRankings().getFirst().getRank());
        assertTrue(movies.pageList(filter).getRecords().stream().anyMatch(r -> r.getId().equals(id.toString())));

        for (String url : List.of("https://book.douban.com/subject/1300267/", "https://example.org/subject/1300267/", "")) {
            movieMapper.update(new LambdaUpdateWrapper<MovieEntity>().eq(MovieEntity::getId, id).set(MovieEntity::getUrl, url));
            assertNull(movieMapper.selectById(id).getPlatform());
            assertNull(movieMapper.selectById(id).getExternalKey());
            assertTrue(movies.getVOById(id).getRankings().isEmpty());
            assertTrue(movies.pageList(filter).getRecords().stream().noneMatch(r -> r.getId().equals(id.toString())));
        }
    }

    @Test
    void 首次可信详情入库后同一外部身份复用() {
        var subject = DoubanSubjectUrl.parse("https://movie.douban.com/subject/90000123456/");
        var first = catalog.resolve(subject, subject.mediaType(), TEST_USER_ID, work -> {
            work.setKind("series");
            work.setTitle("测试剧集");
            work.setEpisodeCount(12);
        });
        var second = catalog.resolve(subject, subject.mediaType(), TEST_USER_ID + 1,
                work -> fail("重复解析试图查询外部详情"));
        assertEquals(first.getId(), second.getId());
        assertNotNull(second.getMetadataReadyTime());
        try (var network = mockStatic(Jsoup.class)) {
            var parsed = movies.parseDouban(subject.canonicalUrl());
            assertEquals(2, parsed.getType());
            assertEquals(12, parsed.getTotalProgress());
            network.verifyNoInteractions();
        }
        verifyNoInteractions(downloader);
    }

    @Test
    void 两类已保存作品缺图时解析保存详情及筛选均走本地() {
        jdbc.update("""
            INSERT INTO sys_user(id,username,nickname) VALUES (?,?,?)
            ON CONFLICT(id) DO NOTHING
            """, TEST_USER_ID, "ranking-test-fixture", "榜单测试");
        var movie = works.findSubject("movie_subject", "1292052");
        var book = works.findSubject("book_subject", "1007305");
        movie.setDurationMinutes(142);
        movie.setMetadataReadyTime(LocalDateTime.now());
        book.setPageCount(1600);
        book.setMetadataReadyTime(LocalDateTime.now());
        works.updateById(movie);
        works.updateById(book);
        long before = jdbc.queryForObject("SELECT count(*) FROM external_resource", Long.class);
        try (var network = mockStatic(Jsoup.class)) {
            var film = movies.parseDouban("https://movie.douban.com/subject/1292052/");
            assertEquals(142, film.getTotalProgress());
            assertNull(film.getCoverImgUrl());
            assertEquals(movie.getId(), film.getRetryCoverWorkId());
            film.setTitle("我的观影记录");
            film.setStatus(ProgressStatusEnum.NOT_STARTED);
            var id = movies.saveRecord(film);
            film.setId(id);
            movies.updateRecord(film);
            var record = movies.getVOById(id);
            assertEquals(movie.getId(), record.getCreativeWorkId());
            assertFalse(record.getRankings().isEmpty());
            assertEquals(movie.getTitle(), movies.parseDouban(film.getUrl()).getTitle());
            var movieQuery = new MovieQuery();
            movieQuery.setRankingListId(record.getRankings().getFirst().getRankingListId());
            assertTrue(movies.pageList(movieQuery).getRecords().stream().anyMatch(r -> r.getId().equals(id.toString())));

            var reading = books.parseDouban("https://book.douban.com/subject/1007305/?from=library");
            assertEquals(1600, reading.getTotalProgress());
            assertNull(reading.getCoverImgUrl());
            reading.setStatus(ProgressStatusEnum.NOT_STARTED);
            var bookId = books.saveRecord(reading);
            var bookRecord = books.getVOById(bookId);
            assertEquals(book.getId(), bookRecord.getCreativeWorkId());
            var bookQuery = new ReadRecordQuery();
            bookQuery.setRankingListId(bookRecord.getRankings().getFirst().getRankingListId());
            assertTrue(books.pageList(bookQuery).getRecords().stream().anyMatch(r -> r.getId().equals(bookId.toString())));
            bookQuery.setRankingListId(movieQuery.getRankingListId());
            assertTrue(books.pageList(bookQuery).getRecords().isEmpty());
            network.verifyNoInteractions();
        }
        verifyNoInteractions(downloader);
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM external_resource", Long.class));
        assertFalse(catalog.rankings("book").isEmpty());
    }
}
