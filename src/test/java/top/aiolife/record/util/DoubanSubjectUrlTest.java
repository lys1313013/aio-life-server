package top.aiolife.record.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoubanSubjectUrlTest {

    @Test
    void parse_电影地址规范化并提取SubjectId() {
        DoubanSubjectUrl.Subject subject = DoubanSubjectUrl.parse(
                "http://movie.douban.com/subject/36354085?from=showing");

        assertEquals(DoubanSubjectUrl.MediaType.MOVIE, subject.mediaType());
        assertEquals("36354085", subject.subjectId());
        assertEquals("https://movie.douban.com/subject/36354085/", subject.canonicalUrl());
    }

    @Test
    void parse_书籍地址规范化并提取SubjectId() {
        DoubanSubjectUrl.Subject subject = DoubanSubjectUrl.parse(
                "https://book.douban.com/subject/4913064/#comments");

        assertEquals(DoubanSubjectUrl.MediaType.BOOK, subject.mediaType());
        assertEquals("4913064", subject.subjectId());
        assertEquals("https://book.douban.com/subject/4913064/", subject.canonicalUrl());
    }

    @Test
    void parse_拒绝非豆瓣域名与非条目路径() {
        IllegalArgumentException hostError = assertThrows(IllegalArgumentException.class,
                () -> DoubanSubjectUrl.parse("https://example.com/subject/36354085/"));
        assertTrue(hostError.getMessage().contains("仅支持"));

        IllegalArgumentException pathError = assertThrows(IllegalArgumentException.class,
                () -> DoubanSubjectUrl.parse("https://movie.douban.com/photos/photo/1/"));
        assertTrue(pathError.getMessage().contains("/subject/{id}/"));

        assertThrows(IllegalArgumentException.class,
                () -> DoubanSubjectUrl.parse("https://movie.douban.com"));
    }

    @Test
    void parse_拒绝利用用户信息伪装域名的地址() {
        assertThrows(IllegalArgumentException.class,
                () -> DoubanSubjectUrl.parse("https://movie.douban.com@127.0.0.1/subject/36354085/"));
    }
}
