package top.aiolife.record.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import top.aiolife.record.pojo.csdn.CsdnArticleVO;
import top.aiolife.record.pojo.csdn.CsdnStatsVO;
import top.aiolife.record.service.ICsdnService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class CsdnServiceImpl implements ICsdnService {

    private static final String CSDN_BASE_URL = "https://blog.csdn.net/";

    private final Cache<String, Document> homepageCache;

    public CsdnServiceImpl() {
        this(Ticker.systemTicker());
    }

    CsdnServiceImpl(Ticker ticker) {
        homepageCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofSeconds(30))
                .ticker(ticker)
                .build();
    }

    /** 统计和文章共用主页；同一账号并发只抓取一次，失败不写入缓存。 */
    private Document getHomepage(String username) throws IOException {
        try {
            return homepageCache.get(username, key -> {
                try {
                    return Jsoup.connect(CSDN_BASE_URL + key)
                            .timeout(10000)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                            .get();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    @Override
    public CsdnStatsVO getCsdnStats(String username) {
        CsdnStatsVO stats = new CsdnStatsVO();
        stats.setViewCount(0);
        stats.setOriginalCount(0);
        stats.setRank(0);
        stats.setFansCount(0);
        stats.setLikeCount(0);
        stats.setCommentCount(0);
        try {
            Document doc = getHomepage(username);

            Elements statNums = doc.select(".user-profile-statistics-num");
            Elements statNames = doc.select(".user-profile-statistics-name");
            
            for (int i = 0; i < Math.min(statNums.size(), statNames.size()); i++) {
                String name = statNames.get(i).text().trim();
                Integer num = parseNumber(statNums.get(i).text());
                
                if ("总访问量".equals(name)) {
                    stats.setViewCount(num);
                } else if ("原创".equals(name)) {
                    stats.setOriginalCount(num);
                } else if ("排名".equals(name)) {
                    stats.setRank(num);
                } else if ("粉丝".equals(name)) {
                    stats.setFansCount(num);
                }
            }

            // likes and comments might be in the achievement box
            stats.setLikeCount(0);
            stats.setCommentCount(0);
            
            Elements achievementTexts = doc.select(".aside-common-box-content-text");
            for (Element el : achievementTexts) {
                String text = el.text();
                if (text.contains("点赞")) {
                    stats.setLikeCount(parseNumber(text));
                } else if (text.contains("评论")) {
                    stats.setCommentCount(parseNumber(text));
                }
            }

        } catch (Exception e) {
            log.warn("CSDN stats request failed: {}", e.getClass().getSimpleName());
            throw new IllegalStateException("CSDN 统计请求失败，请稍后重试", e);
        }
        return stats;
    }

    @Override
    public List<CsdnArticleVO> getCsdnArticles(String username, Integer limit) {
        List<CsdnArticleVO> articles = new ArrayList<>();
        if (limit == null || limit <= 0) {
            limit = 20;
        }
        
        try {
            Document doc = getHomepage(username);

            Elements articleElements = doc.select("article.blog-list-box");
            int count = 0;
            for (Element element : articleElements) {
                if (count >= limit) {
                    break;
                }
                
                CsdnArticleVO article = new CsdnArticleVO();
                
                Element titleElement = element.selectFirst("h4");
                if (titleElement != null) {
                    article.setTitle(titleElement.text().trim());
                }
                
                Element linkElement = element.selectFirst("a");
                if (linkElement != null) {
                    String url = linkElement.attr("href");
                    article.setUrl(url);
                    // Extract ID from url (e.g. https://blog.csdn.net/username/article/details/123456789)
                    if (url.contains("/article/details/")) {
                        article.setId(url.substring(url.lastIndexOf("/") + 1));
                    } else {
                        article.setId(url);
                    }
                }
                
                Element descElement = element.selectFirst(".blog-list-content");
                if (descElement != null) {
                    article.setDescription(descElement.text().trim());
                }
                
                // try to extract views
                Elements viewElements = element.select(".view-num");
                if (!viewElements.isEmpty()) {
                    String viewText = viewElements.get(0).text().trim();
                    article.setViewCount(parseNumber(viewText));
                } else {
                    article.setViewCount(0);
                }
                
                // Extract likes
                Elements likeElements = element.select(".give-like-num");
                if (!likeElements.isEmpty()) {
                    article.setLikeCount(parseNumber(likeElements.get(0).text()));
                } else {
                    article.setLikeCount(0);
                }

                // Extract comments and collects
                Elements commentElements = element.select(".comment-num");
                article.setCommentCount(0);
                article.setCollectCount(0);
                for (Element ce : commentElements) {
                    String ceText = ce.text();
                    if (ceText.contains("评论")) {
                        article.setCommentCount(parseNumber(ceText));
                    } else if (ceText.contains("收藏")) {
                        article.setCollectCount(parseNumber(ceText));
                    }
                }

                // post time
                Element timeElement = element.selectFirst(".view-time-box");
                if (timeElement != null) {
                    String timeText = timeElement.text().trim();
                    // Example text: "博文更新于 2025.06.06 ·"
                    timeText = timeText.replaceAll("博文更新于", "")
                                       .replaceAll("·", "")
                                       .replaceAll(" ", "") // non-breaking space
                                       .trim();
                    article.setPostTime(timeText);
                } else {
                    article.setPostTime("");
                }
                
                articles.add(article);
                count++;
            }

        } catch (Exception e) {
            log.warn("CSDN articles request failed: {}", e.getClass().getSimpleName());
            throw new IllegalStateException("CSDN 文章请求失败，请稍后重试", e);
        }
        return articles;
    }

    private Integer parseNumber(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        try {
            // Remove non-numeric characters except maybe dots (though we just parse int)
            String cleanText = text.replaceAll("[^0-9]", "");
            return Integer.parseInt(cleanText);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
