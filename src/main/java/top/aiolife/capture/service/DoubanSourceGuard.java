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

    public static DoubanSubjectUrl.Subject optionalSubject(String url, DoubanSubjectUrl.MediaType expected) {
        if (url == null || url.isBlank()) return null;
        DoubanSubjectUrl.Subject subject;
        try {
            subject = DoubanSubjectUrl.parse(url);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (subject.mediaType() != expected) throw new IllegalArgumentException("条目与记录类型不一致");
        if (subject.subjectId().length() > 255) throw new IllegalArgumentException("外部条目ID过长");
        return subject;
    }

    public BaseEntity lockExisting(long user, DoubanSubjectUrl.Subject s) {
        // 复用现有用户行锁串行化同一用户的书影写入。
        if (users.selectForAuthUpdate(user) == null) throw CaptureValues.missing();
        return find(user, s, true);
    }

    public BaseEntity find(long user, DoubanSubjectUrl.Subject s, boolean lock) {
        List<? extends BaseEntity> matches;
        if (s.mediaType() == DoubanSubjectUrl.MediaType.BOOK) {
            matches = lock ? reads.captureLockSubjects(user, s.subjectId())
                    : reads.selectList(new LambdaQueryWrapper<ReadRecordEntity>()
                            .eq(ReadRecordEntity::getUserId, user)
                            .eq(ReadRecordEntity::getPlatform, "douban")
                            .eq(ReadRecordEntity::getExternalKey, s.subjectId()));
        } else {
            matches = lock ? movies.captureLockSubjects(user, s.subjectId())
                    : movies.selectList(new LambdaQueryWrapper<MovieEntity>()
                            .eq(MovieEntity::getUserId, user)
                            .eq(MovieEntity::getPlatform, "douban")
                            .eq(MovieEntity::getExternalKey, s.subjectId()));
        }
        if (matches.size() > 1) throw conflict("该豆瓣条目已有多条历史记录，请先在阅读或观影页面处理重复记录");
        return matches.isEmpty() ? null : matches.getFirst();
    }
}
