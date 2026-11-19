package top.aiolife.record.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.core.util.FileContentPolicy;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.enums.AttachmentBizType;
import top.aiolife.record.mapper.CreativeWorkMapper;
import top.aiolife.record.mapper.CreativeWorkMapper.Source;
import top.aiolife.record.mapper.ExternalResourceMapper;
import top.aiolife.record.mapper.RankingListItemMapper;
import top.aiolife.record.mapper.RankingListMapper;
import top.aiolife.record.pojo.entity.CreativeWorkEntity;
import top.aiolife.record.pojo.entity.ExternalResourceEntity;
import top.aiolife.record.pojo.vo.RankedWorkView;
import top.aiolife.record.pojo.vo.RankingBadgeVO;
import top.aiolife.record.pojo.vo.RankingOptionVO;
import top.aiolife.record.util.DoubanSubjectUrl.MediaType;
import top.aiolife.record.util.DoubanSubjectUrl.Subject;
import top.aiolife.system.mapper.StorageObjectMapper;

@Service
@Slf4j
@RequiredArgsConstructor
public class CreativeWorkService {
    private final CreativeWorkMapper works;
    private final RankingListMapper lists;
    private final RankingListItemMapper items;
    private final ExternalResourceService images;
    private final ExternalResourceMapper sources;
    private final StorageObjectMapper objects;
    private final MinioUtil minio;

    public List<RankingOptionVO> rankings(String media) {
        if (!List.of("movie", "book").contains(media)) throw new IllegalArgumentException("榜单类型无效");
        return lists.options(media);
    }

    private Long ensureSubject(Subject subject, MediaType expected) {
        if (subject.mediaType() != expected) throw new IllegalArgumentException("条目与记录类型不一致");
        if (subject.subjectId().length() > 255) throw new IllegalArgumentException("外部条目ID过长");
        boolean book = expected == MediaType.BOOK;
        String type = expected.resourceType();
        var work = works.findSubject(type, subject.subjectId());
        if (work == null) {
            works.register(IdWorker.getId(), type, subject.subjectId(), book ? "book" : "film");
            work = works.findSubject(type, subject.subjectId());
        }
        return work.getId();
    }

    /** 首次解析按作品行锁串行化。个人记录、导入表格及采集草稿均不能写入共享详情。 */
    @Transactional
    public CreativeWorkEntity resolve(Subject subject, MediaType expected, long owner, Consumer<CreativeWorkEntity> fetch) {
        long id = ensureSubject(subject, expected);
        var work = works.selectById(id);
        if (work.getMetadataReadyTime() != null) return work;
        work = works.lock(id);
        if (work.getMetadataReadyTime() != null) return work;
        fetch.accept(work);
        var type = imageType(work);
        if (work.getCoverUrl() != null) {
            try {
                images.identity(work.getCoverUrl(), type);
            } catch (IllegalArgumentException unsupported) {
                work.setCoverUrl(null);
            }
        }
        work.setMetadataReadyTime(LocalDateTime.now());
        work.setUpdateTime(work.getMetadataReadyTime());
        works.updateById(work);
        if (work.getCoverUrl() != null) {
            try {
                images.image(work.getCoverUrl(), type, owner);
            } catch (RuntimeException failure) {
                log.warn("作品 {} 封面暂不可用", id);
            }
        }
        return work;
    }

    public boolean detailsReady(Subject subject) {
        var work = works.findSubject(subject.mediaType().resourceType(), subject.subjectId());
        return work != null && work.getMetadataReadyTime() != null;
    }

    private static String coverPath(CreativeWorkEntity work) {
        return work.getCoverUrl() == null ? null : "/creative-works/" + work.getId() + "/cover";
    }

    private static AttachmentBizType imageType(CreativeWorkEntity work) {
        return "book".equals(work.getKind()) ? AttachmentBizType.READ_RECORD : AttachmentBizType.MOVIE;
    }

    private ExternalResourceEntity coverKey(CreativeWorkEntity work) {
        if (work == null || work.getCoverUrl() == null) return null;
        try {
            return images.identity(work.getCoverUrl(), imageType(work));
        } catch (IllegalArgumentException unsupported) {
            return null;
        }
    }

    private Long cachedObject(CreativeWorkEntity work) {
        var key = coverKey(work);
        if (key == null) return null;
        var source = sources.identity(key);
        return source == null ? null : source.getStorageObjectId();
    }

    public String cachedCoverPath(CreativeWorkEntity work) {
        return cachedObject(work) == null ? null : coverPath(work);
    }

    public void retryCover(long id, long owner) {
        var work = works.selectById(id);
        if (work == null || work.getCoverUrl() == null) throw new IllegalArgumentException("作品缺少封面来源");
        images.image(work.getCoverUrl(), imageType(work), owner);
    }

    public void writeCover(long id, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        var objectId = cachedObject(works.selectById(id));
        var object = objectId == null ? null : objects.selectById(objectId);
        if (object == null) {
            response.setStatus(404);
            return;
        }
        try (var stream = minio.getFile(object.getBucket(), object.getObjectKey())) {
            response.setContentLengthLong(object.getFileSize());
            FileContentPolicy.writeResponse(stream, response, "cover", false, object.getContentType());
        } catch (Exception unavailable) {
            response.setStatus(404);
        }
    }

    public void fill(MediaType media, List<? extends RankedWorkView> records) {
        var identities = records.stream().filter(record -> record.getExternalKey() != null)
                .map(record -> new Source(record.getPlatform(), record.getExternalKey()))
                .collect(Collectors.toSet());
        if (identities.isEmpty()) return;
        var matched = works.findSources(media.resourceType(), identities);
        if (matched.isEmpty()) return;
        Map<Source, Long> workBySource = matched.stream().collect(Collectors.toMap(
                work -> new Source(work.getPlatform(), work.getExternalKey()), CreativeWorkEntity::getId));
        var ids = workBySource.values();
        var byWork = items.badges(ids).stream().collect(Collectors.groupingBy(RankingBadgeVO::getCreativeWorkId));
        var keys = new HashMap<Long, ExternalResourceEntity>();
        for (var work : matched) {
            var key = coverKey(work);
            if (key != null) keys.put(work.getId(), key);
        }
        Set<Long> ready = keys.isEmpty() ? Set.of() : Set.copyOf(sources.readyWorks(keys));
        for (var record : records) {
            Long id = workBySource.get(new Source(record.getPlatform(), record.getExternalKey()));
            if (id == null) continue;
            record.setCreativeWorkId(id);
            record.setRankings(byWork.getOrDefault(id, List.of()));
            if (keys.containsKey(id)) {
                if (ready.contains(id)) record.setCoverImgUrl("/creative-works/" + id + "/cover");
                else record.setRetryCoverWorkId(id);
            }
        }
    }
}
