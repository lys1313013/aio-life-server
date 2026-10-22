package top.aiolife.capture.service;

import static top.aiolife.capture.service.CaptureValues.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import top.aiolife.core.pojo.entity.BaseEntity;
import top.aiolife.record.mapper.IMovieMapper;
import top.aiolife.record.mapper.ReadRecordMapper;
import top.aiolife.record.pojo.entity.MovieEntity;
import top.aiolife.record.pojo.entity.ReadRecordEntity;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;
import top.aiolife.record.service.IMovieService;
import top.aiolife.record.service.IReadRecordService;
import top.aiolife.record.util.DoubanSubjectUrl;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DoubanCaptureService {
    private final IReadRecordService reads;
    private final IMovieService movies;
    private final ReadRecordMapper readMapper;
    private final IMovieMapper movieMapper;
    private final CaptureSecurity access;
    private final DoubanSourceGuard sources;
    private final CaptureRequests requests;
    private final ObjectMapper json;
    private final DoubanSyncCoverService syncCovers;
    private static final String[] EDITABLE = {
        "title",
        "author",
        "director",
        "type",
        "totalProgress",
        "currentProgress",
        "status",
        "remark",
        "rating",
        "fileId"
    };

    public JsonNode preview(long user, ObjectNode request) {
        fields(request, "url", "metadata");
        var subject = subject(user, request);
        ObjectNode draft = json.createObjectNode();
        draft.put("url", subject.canonicalUrl());
        draft.put("subjectId", subject.subjectId());
        draft.put("mediaType", kind(subject));
        ObjectNode metadata =
                request.hasNonNull("metadata")
                        ? object(request.get("metadata"), "metadata")
                        : json.createObjectNode();
        fields(metadata, "title", "author", "director", "type", "totalProgress", "coverImgUrl");
        var warnings = json.createArrayNode();
        if (!metadata.hasNonNull("title") || metadata.path("title").asText().isBlank()) {
            try {
                Object parsed =
                        book(subject)
                                ? reads.parseDoubanMetadata(subject.canonicalUrl())
                                : movies.parseDoubanMetadata(subject.canonicalUrl());
                JsonNode data = json.valueToTree(parsed);
                for (String k :
                        new String[] {
                            "title", "author", "director", "type", "totalProgress", "coverImgUrl"
                        }) if (data.hasNonNull(k)) draft.set(k, data.get(k));
            } catch (RuntimeException e) {
                warnings.add("豆瓣元数据补充失败，请手动补齐标题等字段");
            }
        }
        metadata.fields()
                .forEachRemaining(
                        e -> {
                            if (!e.getValue().isNull()) draft.set(e.getKey(), e.getValue());
                        });
        if (!draft.has("type")) draft.put("type", 1);
        draft.put("status", "not_started");
        draft.put("currentProgress", 0);
        var result = json.createObjectNode();
        result.set("draft", draft);
        result.set("warnings", warnings);
        BaseEntity found = sources.find(user, subject, false);
        result.set("existing", found == null ? json.nullNode() : existingView(found));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public JsonNode commit(long user, ObjectNode request) {
        fields(request, "url", "idempotencyKey", "action", "existingId", "version", "fields");
        var subject = subject(user, request);
        String action = choice(request, "action", Set.of("create", "update"));
        ObjectNode patch = object(request.path("fields"), "fields");
        fields(patch, EDITABLE);
        var key = requests.begin(user, "douban", request);
        JsonNode replay = requests.replay(key);
        if (replay != null) return replay;
        BaseEntity found = sources.lockExisting(user, subject);
        if (found != null)
            found =
                    book(subject)
                            ? readMapper.captureLockOwned(user, found.getId())
                            : movieMapper.captureLockOwned(user, found.getId());
        if (found != null && "create".equals(action))
            return requests.finish(key, result(subject, found, "existing"));
        if ("update".equals(action)) {
            if (found == null || !found.getId().equals(id(request, "existingId", true)))
                throw missing();
            if (!version(found).equals(text(request, "version", true, 64)))
                throw conflict("记录已变化，请重新预览");
        }
        ObjectNode merged = found == null ? json.createObjectNode() : recordFields(found);
        patch.fields().forEachRemaining(e -> merged.set(e.getKey(), e.getValue()));
        String title = text(merged, "title", true, 255);
        int type = integer(merged, "type", 1, book(subject) ? 1 : 5);
        String status =
                merged.hasNonNull("status")
                        ? choice(
                                merged,
                                "status",
                                Set.of("not_started", "in_progress", "completed", "on_hold"))
                        : "not_started";
        int current =
                merged.hasNonNull("currentProgress")
                        ? integer(merged, "currentProgress", 0, 1000000)
                        : 0;
        Integer total =
                merged.hasNonNull("totalProgress")
                        ? integer(merged, "totalProgress", 0, 1000000)
                        : null;
        if (total != null && total > 0 && current > total) throw bad("当前进度不能超过总进度");
        text(merged, "remark", false, 1000);
        text(merged, book(subject) ? "author" : "director", false, 100);
        if (book(subject) && patch.has("rating")) throw bad("书籍不支持个人影视评分");
        if (merged.hasNonNull("rating")) integer(merged, "rating", 1, 5);
        String file = text(merged, "fileId", false, 32);
        access.file(user, file, book(subject) ? "read" : "movie");
        if (book(subject)) {
            ReadRecordEntity entity =
                    found == null ? new ReadRecordEntity() : (ReadRecordEntity) found;
            entity.setTitle(title);
            entity.setType(type);
            entity.setUrl(subject.canonicalUrl());
            entity.setStatus(ProgressStatusEnum.fromCode(status));
            entity.setCurrentProgress(current);
            entity.setTotalProgress(total);
            entity.setAuthor(text(merged, "author", false, 100));
            entity.setRemark(text(merged, "remark", false, 1000));
            entity.setFileId(file);
            entity.setUserId(user);
            if (entity.getStatus() == ProgressStatusEnum.IN_PROGRESS
                    && entity.getStartTime() == null) entity.setStartTime(LocalDateTime.now());
            if (entity.getStatus() == ProgressStatusEnum.COMPLETED
                    && entity.getFinishTime() == null) entity.setFinishTime(LocalDateTime.now());
            if (found == null) {
                entity.fillCreateCommonField(user);
                readMapper.insert(entity);
            } else {
                entity.fillUpdateCommonField(user);
                var w =
                        new LambdaUpdateWrapper<ReadRecordEntity>()
                                .eq(ReadRecordEntity::getId, entity.getId())
                                .eq(ReadRecordEntity::getUserId, user)
                                .set(ReadRecordEntity::getRemark, entity.getRemark())
                                .set(ReadRecordEntity::getFileId, entity.getFileId())
                                .set(ReadRecordEntity::getTotalProgress, total)
                                .set(ReadRecordEntity::getAuthor, entity.getAuthor());
                readMapper.update(entity, w);
            }
            return requests.finish(
                    key, result(subject, entity, found == null ? "created" : "updated"));
        }
        MovieEntity entity = found == null ? new MovieEntity() : (MovieEntity) found;
        entity.setTitle(title);
        entity.setType(type);
        entity.setUrl(subject.canonicalUrl());
        entity.setStatus(ProgressStatusEnum.fromCode(status));
        entity.setCurrentProgress(current);
        entity.setTotalProgress(total);
        entity.setDirector(text(merged, "director", false, 100));
        entity.setRemark(text(merged, "remark", false, 1000));
        entity.setFileId(file);
        entity.setRating(merged.hasNonNull("rating") ? merged.get("rating").intValue() : null);
        entity.setUserId(user);
        if (entity.getStatus() == ProgressStatusEnum.IN_PROGRESS && entity.getStartTime() == null)
            entity.setStartTime(LocalDateTime.now());
        if (entity.getStatus() == ProgressStatusEnum.COMPLETED && entity.getFinishTime() == null)
            entity.setFinishTime(LocalDateTime.now());
        if (found == null) {
            entity.fillCreateCommonField(user);
            movieMapper.insert(entity);
        } else {
            entity.fillUpdateCommonField(user);
            var w =
                    new LambdaUpdateWrapper<MovieEntity>()
                            .eq(MovieEntity::getId, entity.getId())
                            .eq(MovieEntity::getUserId, user)
                            .set(MovieEntity::getRating, entity.getRating())
                            .set(MovieEntity::getRemark, entity.getRemark())
                            .set(MovieEntity::getFileId, entity.getFileId())
                            .set(MovieEntity::getTotalProgress, total)
                            .set(MovieEntity::getDirector, entity.getDirector());
            movieMapper.update(entity, w);
        }
        return requests.finish(key, result(subject, entity, found == null ? "created" : "updated"));
    }

    /** 豆瓣原生标记确认成功后的原子状态同步；绝不以页面元数据覆盖已有个人记录。 */
    @Transactional(rollbackFor = Exception.class)
    public JsonNode sync(long user, ObjectNode request) {
        fields(request, "url", "idempotencyKey", "status", "metadata");
        var subject = DoubanSubjectUrl.parse(text(request, "url", true, 2048));
        if (book(subject)) throw bad("原生标记同步仅支持豆瓣影视条目");
        access.menu(user, "/record/movie");
        String status = choice(request, "status", Set.of("not_started", "completed", "removed"));
        ObjectNode metadata =
                request.hasNonNull("metadata")
                        ? object(request.get("metadata"), "metadata")
                        : json.createObjectNode();
        fields(metadata, "title", "director", "type", "totalProgress", "coverImgUrl");
        var attempt = requests.begin(user, "douban-native-sync", request);
        JsonNode replay = requests.replay(attempt);
        if (replay != null) return replay;

        MovieEntity movie = (MovieEntity) sources.lockExisting(user, subject);
        if ("removed".equals(status)) {
            if (movie != null) {
                // 使用既有逻辑删除语义，保留原行和个人数据，不删除其他用户或封面文件。
                int removed =
                        movieMapper.delete(
                                new LambdaQueryWrapper<MovieEntity>()
                                        .eq(MovieEntity::getId, movie.getId())
                                        .eq(MovieEntity::getUserId, user));
                if (removed != 1) throw conflict("观影记录已变化，请重新同步");
            }
            ObjectNode result = json.createObjectNode();
            if (movie == null) result.putNull("id");
            else result.put("id", movie.getId().toString());
            result.put("mediaType", "movie");
            result.put("operation", movie == null ? "absent" : "deleted");
            result.put("status", status);
            return requests.finish(attempt, result);
        }
        ProgressStatusEnum target = ProgressStatusEnum.fromCode(status);
        var warnings = json.createArrayNode();
        String operation;
        if (movie != null && movie.getStatus() == target) {
            // 相同标记不重写进度或时间，也不重新解析页面、下载封面。
            operation = "unchanged";
        } else if (movie != null) {
            Integer progress =
                    target == ProgressStatusEnum.NOT_STARTED ? 0 : movie.getCurrentProgress();
            if (target == ProgressStatusEnum.COMPLETED
                    && movie.getTotalProgress() != null
                    && movie.getTotalProgress() > 0) progress = movie.getTotalProgress();
            LocalDateTime finished =
                    target == ProgressStatusEnum.COMPLETED ? LocalDateTime.now() : null;
            var update =
                    new LambdaUpdateWrapper<MovieEntity>()
                            .eq(MovieEntity::getId, movie.getId())
                            .eq(MovieEntity::getUserId, user)
                            .set(MovieEntity::getStatus, target)
                            .set(MovieEntity::getCurrentProgress, progress)
                            .set(MovieEntity::getFinishTime, finished)
                            .set(MovieEntity::getUpdateUser, user)
                            .set(MovieEntity::getUpdateTime, LocalDateTime.now());
            if (movieMapper.update(null, update) != 1) throw conflict("观影记录已变化，请重新同步");
            // startTime保留历史开始时间；标题、类型、总进度、封面、评分及备注不参与更新。
            operation = "updated";
        } else {
            ObjectNode draft = metadata.deepCopy();
            if (!draft.hasNonNull("title") || draft.path("title").asText().isBlank()) {
                try {
                    JsonNode parsed =
                            json.valueToTree(movies.parseDoubanMetadata(subject.canonicalUrl()));
                    for (String field :
                            new String[] {
                                "title", "director", "type", "totalProgress", "coverImgUrl"
                            }) {
                        if ((!draft.hasNonNull(field) || draft.path(field).asText().isBlank())
                                && parsed.hasNonNull(field)) {
                            draft.set(field, parsed.get(field));
                        }
                    }
                } catch (RuntimeException error) {
                    warnings.add("影片元数据补充失败，请检查页面提取的信息");
                }
            }
            movie = new MovieEntity();
            movie.setUserId(user);
            movie.setUrl(subject.canonicalUrl());
            movie.setTitle(text(draft, "title", true, 255));
            movie.setDirector(text(draft, "director", false, 100));
            movie.setType(draft.hasNonNull("type") ? integer(draft, "type", 1, 5) : 1);
            int total =
                    draft.hasNonNull("totalProgress")
                            ? integer(draft, "totalProgress", 0, 1000000)
                            : 0;
            movie.setTotalProgress(total);
            movie.setStatus(target);
            movie.setCurrentProgress(
                    target == ProgressStatusEnum.COMPLETED && total > 0 ? total : 0);
            if (target == ProgressStatusEnum.COMPLETED) movie.setFinishTime(LocalDateTime.now());
            String cover = text(draft, "coverImgUrl", false, 2048);
            if (cover != null && !cover.isBlank()) {
                try {
                    movie.setFileId(syncCovers.upload(user, cover));
                } catch (RuntimeException error) {
                    warnings.add("状态已同步，封面暂不可用，可稍后在观影记录中补充");
                }
            } else {
                warnings.add("状态已同步，页面未提供可用封面");
            }
            movie.fillCreateCommonField(user);
            movieMapper.insert(movie);
            operation = "created";
        }
        ObjectNode result = json.createObjectNode();
        result.put("id", movie.getId().toString());
        result.put("mediaType", "movie");
        result.put("operation", operation);
        result.put("status", status);
        if (!warnings.isEmpty()) result.set("warnings", warnings);
        return requests.finish(attempt, result);
    }

    private DoubanSubjectUrl.Subject subject(long user, JsonNode req) {
        var s = DoubanSubjectUrl.parse(text(req, "url", true, 2048));
        access.menu(user, book(s) ? "/record/read" : "/record/movie");
        return s;
    }

    private boolean book(DoubanSubjectUrl.Subject s) {
        return s.mediaType() == DoubanSubjectUrl.MediaType.BOOK;
    }

    private String kind(DoubanSubjectUrl.Subject s) {
        return book(s) ? "book" : "movie";
    }

    private ObjectNode recordFields(BaseEntity e) {
        JsonNode n = json.valueToTree(e);
        ObjectNode out = json.createObjectNode();
        for (String k : EDITABLE) if (n.has(k)) out.set(k, n.get(k));
        out.set("url", n.path("url"));
        return out;
    }

    private String version(BaseEntity e) {
        return requests.hash(json.valueToTree(e));
    }

    private ObjectNode existingView(BaseEntity e) {
        var out = json.createObjectNode();
        out.put("id", e.getId().toString());
        out.put("version", version(e));
        out.set("fields", recordFields(e));
        return out;
    }

    private ObjectNode result(DoubanSubjectUrl.Subject s, BaseEntity e, String operation) {
        // 数据库默认值（如未知总进度0）与秒级审计时间必须参与最终版本，避免刚创建的版本立即过期。
        e = book(s) ? readMapper.selectById(e.getId()) : movieMapper.selectById(e.getId());
        var out = json.createObjectNode();
        out.put("id", e.getId().toString());
        out.put("mediaType", kind(s));
        out.put("operation", operation);
        out.put("version", version(e));
        return out;
    }
}
