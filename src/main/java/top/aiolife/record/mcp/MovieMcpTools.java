package top.aiolife.record.mcp;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import top.aiolife.mcp.annotation.McpToolProvider;
import top.aiolife.record.api.MovieController;
import top.aiolife.record.pojo.query.MovieQuery;
import top.aiolife.record.pojo.vo.MovieVO;
import top.aiolife.record.mcp.req.MovieQueryMcpReq;
import top.aiolife.record.mcp.vo.MovieMcpVO;
import top.aiolife.record.mcp.vo.MoviePageMcpVO;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

import java.util.List;
import java.util.Map;

/**
 * 观影记录 MCP 工具
 *
 * @author Lys
 * @date 2026/08/22
 */
@McpToolProvider
@RequiredArgsConstructor
public class MovieMcpTools {

    private static final Map<ProgressStatusEnum, String> MOVIE_STATUS_LABELS = Map.of(
            ProgressStatusEnum.NOT_STARTED, "想看",
            ProgressStatusEnum.IN_PROGRESS, "在看",
            ProgressStatusEnum.COMPLETED, "看过",
            ProgressStatusEnum.ON_HOLD, "搁置");

    private final MovieController movieController;

    @Tool("分页查询观影记录，供 AI 感知观影进度，status 以中文语义返回")
    public MoviePageMcpVO movie_query(MovieQueryMcpReq req) {
        MovieQuery query = new MovieQuery();
        query.setTitle(req.getTitle());
        query.setDirector(req.getDirector());
        if (req.getStatus() != null) {
            query.setStatus(ProgressStatusEnum.fromCode(req.getStatus()));
        }
        query.setCurrent(req.getPage() == null ? 1 : req.getPage());
        int size = req.getSize() == null ? 10 : req.getSize();
        if (size > 100) {
            size = 100;
        }
        query.setSize(size);

        Page<MovieVO> page = movieController.pageList(query).getData();
        List<MovieMcpVO> records = page.getRecords().stream()
                .map(this::toMcpVO)
                .toList();
        return MoviePageMcpVO.builder()
                .records(records)
                .total(page.getTotal())
                .build();
    }

    private MovieMcpVO toMcpVO(MovieVO vo) {
        MovieMcpVO mcp = new MovieMcpVO();
        mcp.setId(vo.getId() == null ? null : Long.valueOf(vo.getId()));
        mcp.setTitle(vo.getTitle());
        mcp.setType(vo.getType());
        mcp.setDirector(vo.getDirector());
        mcp.setCurrentProgress(vo.getCurrentProgress());
        mcp.setTotalProgress(vo.getTotalProgress());
        mcp.setStartTime(vo.getStartTime());
        mcp.setFinishTime(vo.getFinishTime());
        mcp.setRemark(vo.getRemark());
        mcp.setStatus(statusLabel(vo.getStatus()));
        return mcp;
    }

    private String statusLabel(ProgressStatusEnum status) {
        if (status == null) {
            return null;
        }
        return MOVIE_STATUS_LABELS.get(status);
    }
}
