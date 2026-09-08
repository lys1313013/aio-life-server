package top.aiolife.record.client;

import cn.hutool.core.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import top.aiolife.record.pojo.vo.DoubanAccountVO;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * 查询豆瓣公开个人主页信息。
 */
@Slf4j
@Component
public class DoubanAccountClient {

    private static final String MOBILE_PROFILE_BASE_URL = "https://m.douban.com/people/";
    private static final String PROFILE_BASE_URL = "https://www.douban.com/people/";
    private static final Pattern ACCOUNT_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    public DoubanAccountVO verify(String accountId) {
        String normalizedAccountId = normalizeAccountId(accountId);
        String lookupUrl = MOBILE_PROFILE_BASE_URL + normalizedAccountId + "/";
        String homepageUrl = PROFILE_BASE_URL + normalizedAccountId + "/";

        try {
            Connection.Response response = Jsoup.connect(lookupUrl)
                    .userAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) "
                            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Referer", "https://m.douban.com/")
                    .header("Cookie", "bid=" + RandomUtil.randomString(11) + ";")
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .timeout(8000)
                    .execute();

            if (response.statusCode() == 404) {
                throw new IllegalArgumentException("未找到该豆瓣账号，请检查账号 ID");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("豆瓣账号验证请求失败，accountId={}, status={}",
                        normalizedAccountId, response.statusCode());
                throw new IllegalStateException("豆瓣暂时无法访问，请稍后重试");
            }

            return parseProfile(normalizedAccountId, homepageUrl, response.parse());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (IOException e) {
            log.warn("豆瓣账号验证请求异常，accountId={}", normalizedAccountId, e);
            throw new IllegalStateException("豆瓣暂时无法访问，请稍后重试");
        }
    }

    static String normalizeAccountId(String accountId) {
        String normalized = accountId == null ? "" : accountId.trim();
        if (!ACCOUNT_ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("豆瓣账号 ID 格式不正确，请填写个人主页 /people/ 后面的内容");
        }
        return normalized;
    }

    static DoubanAccountVO parseProfile(String accountId, String homepageUrl, Document document) {
        String title = document.title();
        if (title.contains("页面不存在") || title.contains("你访问的页面不存在")) {
            throw new IllegalArgumentException("未找到该豆瓣账号，请检查账号 ID");
        }

        String nickname = extractNickname(document);
        if (nickname.isBlank() || "豆瓣".equals(nickname) || nickname.contains("登录豆瓣")) {
            throw new IllegalStateException("未能获取豆瓣用户名，请稍后重试");
        }

        return DoubanAccountVO.builder()
                .accountId(accountId)
                .nickname(nickname)
                .homepageUrl(homepageUrl)
                .build();
    }

    static String extractNickname(Document document) {
        Element heading = document.selectFirst("#db-usr-profile .info h1, #db-usr-profile h1, .info h1");
        if (heading != null && !heading.text().isBlank()) {
            return normalizeNickname(heading.text());
        }

        Element openGraphTitle = document.selectFirst("meta[property=og:title]");
        if (openGraphTitle != null && !openGraphTitle.attr("content").isBlank()) {
            return normalizeNickname(openGraphTitle.attr("content"));
        }
        return normalizeNickname(document.title());
    }

    private static String normalizeNickname(String value) {
        String nickname = value == null ? "" : value.trim();
        if (nickname.endsWith(" - 豆瓣")) {
            nickname = nickname.substring(0, nickname.length() - " - 豆瓣".length()).trim();
        }
        if (nickname.endsWith("的个人主页")) {
            nickname = nickname.substring(0, nickname.length() - "的个人主页".length()).trim();
        }
        return nickname;
    }
}
