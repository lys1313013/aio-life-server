package top.aiolife.record.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import top.aiolife.record.convertor.HomeSectionConvertor;
import top.aiolife.record.mapper.HomeSectionMapper;
import top.aiolife.record.pojo.entity.BVideoEntity;
import top.aiolife.record.pojo.entity.DeviceEntity;
import top.aiolife.record.pojo.vo.HomeSectionVO;

/** 两端共用的首页筛选、月份及展示计算口径。 */
@Service
@RequiredArgsConstructor
public class HomeSectionService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final HomeSectionMapper mapper;
    private final HomeSectionAccessGuard access;

    public HomeSectionVO.Items<HomeSectionVO.Memo> memos(long userId) {
        access.check(userId, "/record/memo");
        return new HomeSectionVO.Items<>(mapper.recentMemos(userId).stream()
                .map(HomeSectionConvertor.INSTANCE::memo).toList(), 300);
    }

    public HomeSectionVO.Items<HomeSectionVO.Video> videos(long userId) {
        access.check(userId, "/record/videoWatch");
        return new HomeSectionVO.Items<>(mapper.continuingVideos(userId).stream()
                .map(HomeSectionService::video).toList(), 300);
    }

    public HomeSectionVO.Items<HomeSectionVO.Device> devices(long userId) {
        access.check(userId, "/my-hub/device");
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        return new HomeSectionVO.Items<>(mapper.activeDevices(userId).stream()
                .map(row -> device(row, today)).toList(), 600);
    }

    static HomeSectionVO.Device device(DeviceEntity row, LocalDate today) {
        var result = HomeSectionConvertor.INSTANCE.device(row);
        LocalDate end = row.getEndDate() == null ? today : row.getEndDate();
        if (row.getPurchaseDate() == null) return result;
        int days = Math.toIntExact(Math.max(1, ChronoUnit.DAYS.between(row.getPurchaseDate(), end)));
        result.setUsageDays(days);
        result.setDailyCost(row.getPurchasePrice() == null ? null
                : row.getPurchasePrice().divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP));
        return result;
    }

    static HomeSectionVO.Video video(BVideoEntity row) {
        var result = HomeSectionConvertor.INSTANCE.video(row);
        int episodes = Math.max(1, row.getEpisodes() == null ? 1 : row.getEpisodes());
        int current = Math.min(episodes, Math.max(1, row.getCurrentEpisode() == null ? 1 : row.getCurrentEpisode()));
        int duration = Math.max(0, row.getDuration() == null ? 0 : row.getDuration());
        int watched = Math.max(0, row.getWatchedDuration() == null ? 0 : row.getWatchedDuration());
        result.setEpisodes(episodes);
        result.setCurrentEpisode(current);
        result.setDuration(duration);
        result.setWatchedDuration(watched);
        result.setProgressPercent(duration == 0 ? 0 : (int) Math.min(100, Math.round(watched * 100.0 / duration)));
        result.setResumeUrl(resumeUrl(row, current));
        return result;
    }

    /** 观看时长为跨分集合计，不能误作当前集的 t 参数。 */
    private static String resumeUrl(BVideoEntity row, int episode) {
        String url = row.getUrl();
        if (url == null || url.isBlank()) {
            url = row.getBvid() != null && row.getBvid().matches("BV[a-zA-Z0-9]+")
                    ? "https://www.bilibili.com/video/" + row.getBvid() : null;
        }
        if (url == null) return null;
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) return null;
            if (host == null || !(host.equalsIgnoreCase("bilibili.com")
                    || host.toLowerCase(java.util.Locale.ROOT).endsWith(".bilibili.com"))) return null;
            var builder = UriComponentsBuilder.fromUri(uri).scheme("https");
            var query = builder.build(true).getQueryParams();
            String originalEpisode = query.getFirst("p");
            String offset = query.getFirst("t");
            boolean sameEpisode = originalEpisode == null ? episode == 1 : originalEpisode.equals(String.valueOf(episode));
            if (!sameEpisode || offset == null || !offset.matches("[0-9]{1,9}(\\.[0-9]+)?")) {
                builder.replaceQueryParam("t");
            }
            return builder.replaceQueryParam("p", episode).build(true).toUriString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
