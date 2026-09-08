package top.aiolife.record.client;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import top.aiolife.record.pojo.vo.DoubanAccountVO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DoubanAccountClientTest {

    @Test
    void normalizeAccountId_接受数字和自定义域名() {
        assertEquals("1234567", DoubanAccountClient.normalizeAccountId(" 1234567 "));
        assertEquals("doubanfilm", DoubanAccountClient.normalizeAccountId("doubanfilm"));
    }

    @Test
    void normalizeAccountId_拒绝完整网址和路径字符() {
        assertThrows(IllegalArgumentException.class,
                () -> DoubanAccountClient.normalizeAccountId("https://www.douban.com/people/1234567/"));
        assertThrows(IllegalArgumentException.class,
                () -> DoubanAccountClient.normalizeAccountId("1234567?from=profile"));
    }

    @Test
    void parseProfile_从个人主页标题获取昵称() {
        Document document = Jsoup.parse("""
                <html><head><title>豆瓣电影的个人主页 - 豆瓣</title></head><body></body></html>
                """);

        DoubanAccountVO result = DoubanAccountClient.parseProfile(
                "doubanfilm", "https://www.douban.com/people/doubanfilm/", document);

        assertEquals("doubanfilm", result.getAccountId());
        assertEquals("豆瓣电影", result.getNickname());
        assertEquals("https://www.douban.com/people/doubanfilm/", result.getHomepageUrl());
    }

    @Test
    void parseProfile_优先从页面用户信息获取昵称() {
        Document document = Jsoup.parse("""
                <html><head><title>其他标题 - 豆瓣</title></head><body>
                  <div id="db-usr-profile"><div class="info"><h1>测试豆友</h1></div></div>
                </body></html>
                """);

        DoubanAccountVO result = DoubanAccountClient.parseProfile(
                "1234567", "https://www.douban.com/people/1234567/", document);

        assertEquals("测试豆友", result.getNickname());
    }

    @Test
    void parseProfile_识别不存在页面() {
        Document document = Jsoup.parse("<html><head><title>页面不存在 - 豆瓣</title></head></html>");

        assertThrows(IllegalArgumentException.class, () -> DoubanAccountClient.parseProfile(
                "missing", "https://www.douban.com/people/missing/", document));
    }
}
