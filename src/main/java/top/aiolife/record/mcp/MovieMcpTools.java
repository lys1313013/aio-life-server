package top.aiolife.record.mcp;

import cn.hutool.core.bean.BeanUtil;
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

    private static final Map<Integer, String> MOVIE_STATUS_LABELS = Map.of(
            0, "想看",
            1, "在看",
            2, "看过",
            3, "搁置");

    private final MovieController movieController;

    @Tool("分页查询观影记录，供 AI 感知观影进度，status 以中文语义返回")
    public MoviePageMcpVO movie_query(MovieQueryMcpReq req) {
        MovieQuery query = new MovieQuery();
        BeanUtil.copyProperties(req, query);
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
        BeanUtil.copyProperties(vo, mcp);
        mcp.setId(vo.getId() == null ? null : Long.valueOf(vo.getId()));
        mcp.setStatus(statusLabel(vo.getStatus()));
        return mcp;
    }

    private String statusLabel(Integer code) {
        if (code == null) {
            return null;
        }
        return MOVIE_STATUS_LABELS.getOrDefault(code, String.valueOf(code));
    }
}