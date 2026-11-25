package top.aiolife.record.util;

import cn.hutool.core.util.StrUtil;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 豆瓣条目地址解析与规范化。
 */
public final class DoubanSubjectUrl {

    private static final Pattern SUBJECT_PATH = Pattern.compile("^/subject/(\\d+)/?$");
    private static final Pattern SHARE_PATH = Pattern.compile("^/doubanapp/dispatch/(movie|book)/(\\d+)/?$");

    private DoubanSubjectUrl() {
    }

    public static Subject parse(String url) {
        if (StrUtil.isBlank(url)) {
            throw new IllegalArgumentException("豆瓣地址不能为空");
        }

        final URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("豆瓣地址格式不正确");
        }

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("豆瓣地址仅支持 http 或 https");
        }
        if (uri.getUserInfo() != null || uri.getPort() != -1) {
            throw new IllegalArgumentException("豆瓣地址不能包含用户信息或端口");
        }

        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("豆瓣地址格式不正确");
        }

        return switch (host.toLowerCase(Locale.ROOT)) {
            case "movie.douban.com" -> parseSubjectPath(uri.getPath(), MediaType.MOVIE);
            case "book.douban.com" -> parseSubjectPath(uri.getPath(), MediaType.BOOK);
            case "www.douban.com" -> parseSharePath(uri.getRawPath());
            default -> throw new IllegalArgumentException("仅支持豆瓣书影条目地址或 App 分享链接");
        };
    }

    private static Subject parseSubjectPath(String path, MediaType mediaType) {
        Matcher matcher = SUBJECT_PATH.matcher(path);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("豆瓣地址必须是 /subject/{id}/ 格式");
        }
        return subject(mediaType, matcher.group(1));
    }

    private static Subject parseSharePath(String path) {
        Matcher matcher = SHARE_PATH.matcher(path);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("豆瓣 App 分享链接必须指向影视或书籍条目");
        }
        MediaType mediaType = MediaType.MOVIE.domain.equals(matcher.group(1)) ? MediaType.MOVIE : MediaType.BOOK;
        return subject(mediaType, matcher.group(2));
    }

    private static Subject subject(MediaType mediaType, String subjectId) {
        String canonicalUrl = "https://" + mediaType.domain + ".douban.com/subject/" + subjectId + "/";
        return new Subject(mediaType, subjectId, canonicalUrl);
    }

    public static boolean matches(String url, MediaType mediaType, String subjectId) {
        try {
            Subject subject = parse(url);
            return subject.mediaType() == mediaType && subject.subjectId().equals(subjectId);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public enum MediaType {
        MOVIE("movie"),
        BOOK("book");

        private final String domain;
        private final String resourceType;

        MediaType(String domain) {
            this.domain = domain;
            resourceType = domain + "_subject";
        }

        public String resourceType() {
            return resourceType;
        }
    }

    public record Subject(MediaType mediaType, String subjectId, String canonicalUrl) {
    }
}
