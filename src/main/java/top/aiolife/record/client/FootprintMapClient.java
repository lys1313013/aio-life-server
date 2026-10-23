package top.aiolife.record.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.record.pojo.vo.FootprintLocationVO;
import top.aiolife.system.service.FootprintMapConfigService;

/** 查询编排：百度优先，额度耗尽、并发受限或额度状态不可读时调用腾讯；一次业务请求共用限流及并发许可。 */
@Component
public class FootprintMapClient {
    private final FootprintMapProvider baidu;
    private final FootprintMapProvider tencent;
    private final FootprintMapConfigService config;
    private final Semaphore concurrent = new Semaphore(8);
    private final Cache<Long, AtomicInteger> limits = Caffeine.newBuilder()
            .maximumSize(10000).expireAfterWrite(Duration.ofMinutes(1)).build();

    public FootprintMapClient(@Qualifier("baiduFootprintMapProvider") FootprintMapProvider baidu,
                              @Qualifier("tencentFootprintMapProvider") FootprintMapProvider tencent,
                              FootprintMapConfigService config) {
        this.baidu = baidu;
        this.tencent = tencent;
        this.config = config;
    }

    public boolean available() { return baidu.available() || tencent.available(); }
    public FootprintMapConfigService.TileSource tileSource() { return config.tileSource(); }

    public List<FootprintLocationVO> search(long userId, String keyword, String region) {
        return query(userId, provider -> provider.search(keyword, region));
    }

    public FootprintLocationVO geocode(long userId, String address) {
        return query(userId, provider -> provider.geocode(address));
    }

    public FootprintLocationVO reverse(long userId, double longitude, double latitude) {
        return query(userId, provider -> provider.reverse(longitude, latitude));
    }

    private <T> T query(long userId, Function<FootprintMapProvider, T> action) {
        if (limits.get(userId, ignored -> new AtomicInteger()).incrementAndGet() > 60 || !concurrent.tryAcquire())
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "搜索较频繁，请稍后重试");
        try {
            if (baidu.available()) {
                try {
                    return action.apply(baidu);
                } catch (MapFallbackException unavailable) {
                    if (!tencent.available())
                        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                                unavailable.getReason() + "；尚未配置腾讯搜索解析 Key，可手工选点");
                    return action.apply(tencent);
                }
            }
            if (tencent.available()) return action.apply(tencent);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "请在系统配置填写百度 AK 或腾讯搜索解析 Key");
        } finally {
            concurrent.release();
        }
    }
}
