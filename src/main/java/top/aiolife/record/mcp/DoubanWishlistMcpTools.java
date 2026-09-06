package top.aiolife.record.mcp;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import top.aiolife.mcp.annotation.McpToolProvider;
import top.aiolife.record.mcp.req.DoubanWishlistAddMcpReq;
import top.aiolife.record.mcp.vo.DoubanWishlistAddMcpVO;
import top.aiolife.record.pojo.entity.MovieEntity;
import top.aiolife.record.pojo.entity.ReadRecordEntity;
import top.aiolife.record.pojo.req.MovieReq;
import top.aiolife.record.pojo.req.ReadRecordReq;
import top.aiolife.record.service.IMovieService;
import top.aiolife.record.service.IReadRecordService;
import top.aiolife.record.util.DoubanSubjectUrl;

/**
 * 豆瓣想看/想读 MCP 工具。
 */
@McpToolProvider
@RequiredArgsConstructor
public class DoubanWishlistMcpTools {

    private final IMovieService movieService;
    private final IReadRecordService readRecordService;

    @Tool("根据豆瓣电影或书籍条目地址录入想看/想读记录；后端自动识别类型并解析条目信息，同一用户重复录入同一 subjectId 时返回已有记录")
    public DoubanWishlistAddMcpVO douban_wishlist_add(DoubanWishlistAddMcpReq req) {
        if (req == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        DoubanSubjectUrl.Subject subject = DoubanSubjectUrl.parse(req.getUrl());
        return switch (subject.mediaType()) {
            case MOVIE -> addMovie(subject);
            case BOOK -> addBook(subject);
        };
    }

    private DoubanWishlistAddMcpVO addMovie(DoubanSubjectUrl.Subject subject) {
        MovieEntity existing = movieService.findByDoubanSubjectId(subject.subjectId());
        if (existing != null) {
            if (StrUtil.isBlank(existing.getFileId())) {
                MovieReq parsed = movieService.parseDouban(subject.canonicalUrl());
                requireCoverFileId(parsed.getFileId(), "电影");
                movieService.updateCoverFileId(existing.getId(), parsed.getFileId());
                existing.setFileId(parsed.getFileId());
            }
            return movieResult(existing, false, subject.canonicalUrl());
        }

        MovieReq parsed = movieService.parseDouban(subject.canonicalUrl());
        requireCoverFileId(parsed.getFileId(), "电影");
        parsed.setUrl(subject.canonicalUrl());
        parsed.setStatus(0);
        if (parsed.getCurrentProgress() == null) {
            parsed.setCurrentProgress(0);
        }
        Long id = movieService.saveRecord(parsed);
        return DoubanWishlistAddMcpVO.builder()
                .created(true)
                .mediaType("movie")
                .id(id)
                .title(parsed.getTitle())
                .status("想看")
                .fileId(parsed.getFileId())
                .url(subject.canonicalUrl())
                .build();
    }

    private DoubanWishlistAddMcpVO addBook(DoubanSubjectUrl.Subject subject) {
        ReadRecordEntity existing = readRecordService.findByDoubanSubjectId(subject.subjectId());
        if (existing != null) {
            if (StrUtil.isBlank(existing.getFileId())) {
                ReadRecordReq parsed = readRecordService.parseDouban(subject.canonicalUrl());
                requireCoverFileId(parsed.getFileId(), "书籍");
                readRecordService.updateCoverFileId(existing.getId(), parsed.getFileId());
                existing.setFileId(parsed.getFileId());
            }
            return bookResult(existing, false, subject.canonicalUrl());
        }

        ReadRecordReq parsed = readRecordService.parseDouban(subject.canonicalUrl());
        requireCoverFileId(parsed.getFileId(), "书籍");
        parsed.setUrl(subject.canonicalUrl());
        parsed.setStatus(0);
        if (parsed.getCurrentProgress() == null) {
            parsed.setCurrentProgress(0);
        }
        Long id = readRecordService.saveRecord(parsed);
        return DoubanWishlistAddMcpVO.builder()
                .created(true)
                .mediaType("book")
                .id(id)
                .title(parsed.getTitle())
                .status("想读")
                .fileId(parsed.getFileId())
                .url(subject.canonicalUrl())
                .build();
    }

    private DoubanWishlistAddMcpVO movieResult(MovieEntity entity, boolean created, String canonicalUrl) {
        return DoubanWishlistAddMcpVO.builder()
                .created(created)
                .mediaType("movie")
                .id(entity.getId())
                .title(entity.getTitle())
                .status(movieStatus(entity.getStatus()))
                .fileId(entity.getFileId())
                .url(canonicalUrl)
                .build();
    }

    private DoubanWishlistAddMcpVO bookResult(ReadRecordEntity entity, boolean created, String canonicalUrl) {
        return DoubanWishlistAddMcpVO.builder()
                .created(created)
                .mediaType("book")
                .id(entity.getId())
                .title(entity.getTitle())
                .status(bookStatus(entity.getStatus()))
                .fileId(entity.getFileId())
                .url(canonicalUrl)
                .build();
    }

    private void requireCoverFileId(String fileId, String mediaLabel) {
        if (StrUtil.isBlank(fileId)) {
            throw new IllegalStateException(mediaLabel + "封面上传失败，未保存记录，请检查 MinIO 后重试");
        }
    }

    private String movieStatus(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 0 -> "想看";
            case 1 -> "在看";
            case 2 -> "看过";
            case 3 -> "搁置";
            default -> String.valueOf(status);
        };
    }

    private String bookStatus(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 0 -> "想读";
            case 1 -> "阅读中";
            case 2 -> "已读完";
            case 3 -> "搁置";
            default -> String.valueOf(status);
        };
    }
}
