package top.aiolife.record.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

class DoubanCoverUrlPolicyTest {
    private final DoubanCoverUrlPolicy policy = new DoubanCoverUrlPolicy(
            "doubanio.com");

    @ParameterizedTest
    @ValueSource(strings = {
            "https://img1.doubanio.com/view/photo/l/public/cover.jpg",
            "https://img2.doubanio.com/cover.jpg?size=large",
            "https://img3.doubanio.com:443/cover.jpg",
            "https://IMG9.DOUBANIO.COM/cover.jpg",
            "https://new-cdn.doubanio.com/cover.jpg",
            "https://sub.img1.doubanio.com/cover.jpg",
            "https://doubanio.com/cover.jpg"
    })
    void validate_允许白名单域名及任意子域名HTTPS地址(String url) {
        assertEquals(url, policy.validate(url));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "https://127.0.0.1/cover.jpg", "https://10.0.0.1/cover.jpg",
            "https://169.254.169.254/cover.jpg", "https://[::1]/cover.jpg",
            "https://localhost/cover.jpg", "https://example.com/cover.jpg",
            "https://img1.doubanio.com.evil.example/cover.jpg",
            "https://fakedoubanio.com/cover.jpg", "https://doubanio.com.evil.example/cover.jpg",
            "https://img1.doubanio.com@127.0.0.1/cover.jpg",
            "https://user:password@img1.doubanio.com/cover.jpg",
            "https://example.com/?url=https://img1.doubanio.com/cover.jpg",
            "https://img1.doubanio.com:8443/cover.jpg",
            "https://img1.doubanio.com./cover.jpg", "https://img1%2edoubanio.com/cover.jpg",
            "https://img1.doubanio.com/cover.jpg#fragment",
            "https://img1.doubanio.com\\@127.0.0.1/cover.jpg",
            "http://img1.doubanio.com/cover.jpg", "file:///etc/hosts",
            "ftp://img1.doubanio.com/cover.jpg", "//img1.doubanio.com/cover.jpg",
            " https://img1.doubanio.com/cover.jpg", "not a url"
    })
    void validate_拒绝非白名单和歧义URL(String url) {
        assertThrows(IllegalArgumentException.class, () -> policy.validate(url));
    }

    @Test
    void validate_配置可替换白名单且空值拒绝全部下载() {
        new ApplicationContextRunner().withUserConfiguration(DoubanCoverUrlPolicy.class)
                .withPropertyValues("aio.life.server.douban.cover-allowed-domains= IMG2.DOUBANIO.COM ")
                .run(context -> {
                    var configured = context.getBean(DoubanCoverUrlPolicy.class);
                    assertDoesNotThrow(() -> configured.validate("https://img2.doubanio.com/cover.jpg"));
                    assertDoesNotThrow(() -> configured.validate("https://nested.img2.doubanio.com/cover.jpg"));
                    assertThrows(IllegalArgumentException.class,
                            () -> configured.validate("https://img1.doubanio.com/cover.jpg"));
                });
        assertThrows(IllegalArgumentException.class,
                () -> new DoubanCoverUrlPolicy("").validate("https://img1.doubanio.com/cover.jpg"));
        assertThrows(IllegalArgumentException.class,
                () -> new DoubanCoverUrlPolicy("*.doubanio.com").validate("https://img1.doubanio.com/cover.jpg"));
    }
}
