package top.aiolife.record.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.record.mapper.IMovieMapper;
import top.aiolife.record.pojo.entity.MovieEntity;
import top.aiolife.record.pojo.entity.UserBindEntity;
import top.aiolife.record.pojo.req.DoubanMovieImportItemReq;
import top.aiolife.record.pojo.req.DoubanMovieImportReq;
import top.aiolife.record.pojo.vo.DoubanMovieImportPreviewVO;
import top.aiolife.record.pojo.vo.DoubanMovieImportResultVO;
import top.aiolife.record.service.IDoubanMovieImportService;
import top.aiolife.record.service.IUserBindService;
import top.aiolife.record.util.DoubanSubjectUrl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DoubanMovieImportServiceImpl implements IDoubanMovieImportService {

    private static final String FORMAT = "aio-life-movie-import";
    private static final int VERSION = 1;
    private static final int MAX_RECORDS = 10_000;

    private final IMovieMapper movieMapper;
    private final IUserBindService userBindService;

    @Override
    public DoubanMovieImportPreviewVO preview(DoubanMovieImportReq request) {
        return inspect(request, StpUtil.getLoginIdAsLong()).preview();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DoubanMovieImportResultVO importRecords(DoubanMovieImportReq request) {
        Long userId = StpUtil.getLoginIdAsLong();
        Inspection inspection = inspect(request, userId);
        if (!inspection.preview().getErrors().isEmpty()) {
            DoubanMovieImportPreviewVO.ErrorItem first = inspection.preview().getErrors().get(0);
            throw new IllegalArgumentException("第 " + first.getRowNumber() + " 行：" + first.getMessage());
        }

        String policy = StrUtil.blankToDefault(request.getDuplicatePolicy(), "skip")
                .trim().toLowerCase(Locale.ROOT);
        if (!Set.of("skip", "overwrite").contains(policy)) {
            throw new IllegalArgumentException("重复记录处理方式仅支持 skip 或 overwrite");
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        // 正式写入前的 inspect 是第二次判重，不能复用前端预览结果。
        for (NormalizedItem item : inspection.items()) {
            MovieEntity existing = inspection.existingBySubjectId().get(item.subjectId());
            if (existing != null) {
                if ("skip".equals(policy)) {
                    skipped++;
                    continue;
                }
                applyImportedFields(existing, item, userId, false);
                movieMapper.updateById(existing);
                updated++;
                continue;
            }

            MovieEntity entity = new MovieEntity();
            entity.setUserId(userId);
            applyImportedFields(entity, item, userId, true);
            movieMapper.insert(entity);
            inspection.existingBySubjectId().put(item.subjectId(), entity);
            created++;
        }
        return new DoubanMovieImportResultVO(created, updated, skipped);
    }

    private Inspection inspect(DoubanMovieImportReq request, Long userId) {
        DoubanMovieImportPreviewVO preview = new DoubanMovieImportPreviewVO();
        if (request == null) {
            addError(preview, 0, "导入内容不能为空");
            return new Inspection(preview, List.of(), new HashMap<>());
        }

        List<DoubanMovieImportItemReq> records = request.getRecords() == null
                ? List.of() : request.getRecords();
        preview.setTotal(records.size());
        validateMetadata(request, userId, preview);
        if (records.isEmpty()) {
            addError(preview, 0, "Movies 工作表中没有可导入记录");
        } else if (records.size() > MAX_RECORDS) {
            addError(preview, 0, "单次最多导入 " + MAX_RECORDS + " 条记录");
        }

        Map<String, MovieEntity> existingBySubjectId = loadExisting(userId);
        Set<String> fileSubjectIds = new HashSet<>();
        List<NormalizedItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            DoubanMovieImportItemReq row = records.get(i);
            int rowNumber = row != null && row.getRowNumber() != null ? row.getRowNumber() : i + 2;
            try {
                NormalizedItem item = normalize(row, rowNumber);
                if (!fileSubjectIds.add(item.subjectId())) {
                    addError(preview, rowNumber, "文件内存在重复的 douban_subject_id");
                    continue;
                }
                items.add(item);
                MovieEntity existing = existingBySubjectId.get(item.subjectId());
                if (existing == null) {
                    preview.setNewCount(preview.getNewCount() + 1);
                } else {
                    preview.setDuplicateCount(preview.getDuplicateCount() + 1);
                    preview.getDuplicates().add(new DoubanMovieImportPreviewVO.DuplicateItem(
                            rowNumber, item.subjectId(), item.row().getTitle(),
                            String.valueOf(existing.getId()), existing.getTitle()));
                }
            } catch (IllegalArgumentException exception) {
                addError(preview, rowNumber, exception.getMessage());
            }
        }
        preview.setErrorCount(preview.getErrors().size());
        return new Inspection(preview, items, existingBySubjectId);
    }

    private void validateMetadata(DoubanMovieImportReq request, Long userId,
                                  DoubanMovieImportPreviewVO preview) {
        if (!FORMAT.equals(request.getFormat())) {
            addError(preview, 0, "文件 format 必须为 " + FORMAT);
        }
        if (request.getVersion() == null || request.getVersion() != VERSION) {
            addError(preview, 0, "仅支持 version=1 的导入文件");
        }
        if (!"douban".equals(request.getSource())) {
            addError(preview, 0, "文件 source 必须为 douban");
        }
        UserBindEntity binding = userBindService.getBindByUserIdAndPlatform(userId, "douban");
        if (binding == null || StrUtil.isBlank(binding.getPlatformUsername())) {
            addError(preview, 0, "请先在账号绑定中绑定豆瓣账号 ID");
        } else if (StrUtil.isBlank(request.getDoubanUserId())
                || !binding.getPlatformUsername().trim().equals(request.getDoubanUserId().trim())) {
            addError(preview, 0, "导出文件的豆瓣账号 ID 与当前绑定账号不一致");
        }
    }

    private Map<String, MovieEntity> loadExisting(Long userId) {
        List<MovieEntity> movies = movieMapper.selectList(new LambdaQueryWrapper<MovieEntity>()
                .eq(MovieEntity::getUserId, userId)
                .isNotNull(MovieEntity::getUrl));
        Map<String, MovieEntity> result = new HashMap<>();
        for (MovieEntity movie : movies) {
            try {
                DoubanSubjectUrl.Subject subject = DoubanSubjectUrl.parse(movie.getUrl());
                if (subject.mediaType() == DoubanSubjectUrl.MediaType.MOVIE) {
                    result.putIfAbsent(subject.subjectId(), movie);
                }
            } catch (IllegalArgumentException ignored) {
                // 非豆瓣链接不参与本次判重。
            }
        }
        return result;
    }

    private NormalizedItem normalize(DoubanMovieImportItemReq row, int rowNumber) {
        if (row == null) {
            throw new IllegalArgumentException("记录不能为空");
        }
        String subjectId = required(row.getDoubanSubjectId(), "douban_subject_id");
        if (!subjectId.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("douban_subject_id 必须为数字");
        }
        String title = required(row.getTitle(), "title");
        if (title.length() > 255) {
            throw new IllegalArgumentException("title 不能超过 255 个字符");
        }
        Integer type = switch (required(row.getType(), "type")) {
            case "movie" -> 1;
            case "series" -> 2;
            case "animation" -> 3;
            case "documentary" -> 4;
            case "other" -> 5;
            default -> throw new IllegalArgumentException("type 不在允许范围内");
        };
        DoubanSubjectUrl.Subject subject = DoubanSubjectUrl.parse(required(row.getUrl(), "url"));
        if (subject.mediaType() != DoubanSubjectUrl.MediaType.MOVIE
                || !subject.subjectId().equals(subjectId)) {
            throw new IllegalArgumentException("url 与 douban_subject_id 不一致");
        }
        if (row.getStatus() == null) {
            throw new IllegalArgumentException("status 不能为空");
        }
        if (row.getRating() != null && (row.getRating() < 1 || row.getRating() > 5)) {
            throw new IllegalArgumentException("rating 必须为 1-5 的整数");
        }
        if (StrUtil.length(row.getDirector()) > 100) {
            throw new IllegalArgumentException("director 不能超过 100 个字符");
        }
        if (StrUtil.length(row.getRemark()) > 1000) {
            throw new IllegalArgumentException("remark 不能超过 1000 个字符");
        }
        row.setTitle(title);
        return new NormalizedItem(rowNumber, subjectId, subject.canonicalUrl(), type, row);
    }

    private void applyImportedFields(MovieEntity entity, NormalizedItem item,
                                     Long userId, boolean creating) {
        DoubanMovieImportItemReq row = item.row();
        entity.setTitle(row.getTitle());
        entity.setType(item.type());
        entity.setUrl(item.canonicalUrl());
        entity.setStatus(row.getStatus());
        if (StrUtil.isNotBlank(row.getDirector())) {
            entity.setDirector(row.getDirector().trim());
        }
        if (row.getMarkedDate() != null) {
            entity.setFinishTime(row.getMarkedDate().atStartOfDay());
        }
        if (row.getRating() != null) {
            entity.setRating(row.getRating());
        }
        if (StrUtil.isNotBlank(row.getRemark())) {
            entity.setRemark(row.getRemark().trim());
        }
        if (creating) {
            entity.fillCreateCommonField(userId);
        } else {
            entity.fillUpdateCommonField(userId);
        }
    }

    private static String required(String value, String field) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private static void addError(DoubanMovieImportPreviewVO preview, int rowNumber, String message) {
        preview.getErrors().add(new DoubanMovieImportPreviewVO.ErrorItem(rowNumber, message));
        preview.setErrorCount(preview.getErrors().size());
    }

    private record NormalizedItem(int rowNumber, String subjectId, String canonicalUrl,
                                  Integer type, DoubanMovieImportItemReq row) {
    }

    private record Inspection(DoubanMovieImportPreviewVO preview, List<NormalizedItem> items,
                              Map<String, MovieEntity> existingBySubjectId) {
    }
}
