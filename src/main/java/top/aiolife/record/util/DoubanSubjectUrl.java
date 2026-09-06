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

        MediaType mediaType = switch (host.toLowerCase(Locale.ROOT)) {
            case "movie.douban.com" -> MediaType.MOVIE;
            case "book.douban.com" -> MediaType.BOOK;
            default -> throw new IllegalArgumentException("仅支持 movie.douban.com 或 book.douban.com 的条目地址");
        };

        String path = uri.getPath();
        if (path == null) {
            throw new IllegalArgumentException("豆瓣地址必须是 /subject/{id}/ 格式");
        }
        Matcher matcher = SUBJECT_PATH.matcher(path);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("豆瓣地址必须是 /subject/{id}/ 格式");
        }

        String subjectId = matcher.group(1);
        String canonicalUrl = "https://" + host.toLowerCase(Locale.ROOT) + "/subject/" + subjectId + "/";
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
        MOVIE,
        BOOK
    }

    public record Subject(MediaType mediaType, String subjectId, String canonicalUrl) {
    }
}
