package top.aiolife.capture.service;

import static top.aiolife.capture.service.CaptureValues.conflict;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import top.aiolife.core.pojo.entity.BaseEntity;
import top.aiolife.record.mapper.IMovieMapper;
import top.aiolife.record.mapper.ReadRecordMapper;
import top.aiolife.record.pojo.entity.MovieEntity;
import top.aiolife.record.pojo.entity.ReadRecordEntity;
import top.aiolife.record.util.DoubanSubjectUrl;
import top.aiolife.sso.mapper.UserMapper;

import java.util.List;

/** 所有豆瓣新增入口共享数据库来源锁；必须在事务内调用 lockExisting。 */
@Service
@RequiredArgsConstructor
public class DoubanSourceGuard {
    private final UserMapper users;
    private final IMovieMapper movies;
    private final ReadRecordMapper reads;

    public static DoubanSubjectUrl.Subject optionalSubject(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            return DoubanSubjectUrl.parse(url);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public BaseEntity lockExisting(long user, DoubanSubjectUrl.Subject s) {
        // 复用现有用户行锁串行化该用户的书影写入，不增加表、字段或来源持久化。
        if (users.selectForAuthUpdate(user) == null) throw CaptureValues.missing();
        return find(user, s, true);
    }

    public BaseEntity find(long user, DoubanSubjectUrl.Subject s, boolean lock) {
        List<? extends BaseEntity> matches;
        if (s.mediaType() == DoubanSubjectUrl.MediaType.BOOK) {
            List<ReadRecordEntity> candidates =
                    lock
                            ? reads.captureLockSubjects(user, s.subjectId())
                            : reads.selectList(
                                    new LambdaQueryWrapper<ReadRecordEntity>()
                                            .eq(ReadRecordEntity::getUserId, user)
                                            .like(
                                                    ReadRecordEntity::getUrl,
                                                    "/subject/" + s.subjectId()));
            matches =
                    candidates.stream()
                            .filter(
                                    e ->
                                            DoubanSubjectUrl.matches(
                                                    e.getUrl(), s.mediaType(), s.subjectId()))
                            .toList();
        } else {
            List<MovieEntity> candidates =
                    lock
                            ? movies.captureLockSubjects(user, s.subjectId())
                            : movies.selectList(
                                    new LambdaQueryWrapper<MovieEntity>()
                                            .eq(MovieEntity::getUserId, user)
                                            .like(
                                                    MovieEntity::getUrl,
                                                    "/subject/" + s.subjectId()));
            matches =
                    candidates.stream()
                            .filter(
                                    e ->
                                            DoubanSubjectUrl.matches(
                                                    e.getUrl(), s.mediaType(), s.subjectId()))
                            .toList();
        }
        if (matches.size() > 1) throw conflict("该豆瓣条目已有多条历史记录，请先在阅读或观影页面处理重复记录");
        return matches.isEmpty() ? null : matches.getFirst();
    }
}
