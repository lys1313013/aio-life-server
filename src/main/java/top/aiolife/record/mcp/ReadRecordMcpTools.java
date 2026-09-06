package top.aiolife.record.mcp;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import top.aiolife.mcp.annotation.McpToolProvider;
import top.aiolife.record.api.ReadRecordController;
import top.aiolife.record.pojo.query.ReadRecordQuery;
import top.aiolife.record.pojo.vo.ReadRecordVO;
import top.aiolife.record.mcp.req.ReadRecordQueryMcpReq;
import top.aiolife.record.mcp.vo.ReadRecordMcpVO;
import top.aiolife.record.mcp.vo.ReadRecordPageMcpVO;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

import java.util.List;
import java.util.Map;

/**
 * 阅读记录 MCP 工具
 *
 * @author Lys
 * @date 2026/08/22
 */
@McpToolProvider
@RequiredArgsConstructor
public class ReadRecordMcpTools {

    private static final Map<ProgressStatusEnum, String> READ_STATUS_LABELS = Map.of(
            ProgressStatusEnum.NOT_STARTED, "想看",
            ProgressStatusEnum.IN_PROGRESS, "在看",
            ProgressStatusEnum.COMPLETED, "看过",
            ProgressStatusEnum.ON_HOLD, "搁置");

    private final ReadRecordController readRecordController;

    @Tool("分页查询阅读记录，供 AI 感知读书进度，status 以中文语义返回")
    public ReadRecordPageMcpVO read_record_query(ReadRecordQueryMcpReq req) {
        ReadRecordQuery query = new ReadRecordQuery();
        query.setTitle(req.getTitle());
        if (req.getStatus() != null) {
            query.setStatus(ProgressStatusEnum.fromCode(req.getStatus()));
        }
        query.setCurrent(req.getPage() == null ? 1 : req.getPage());
        int size = req.getSize() == null ? 10 : req.getSize();
        if (size > 100) {
            size = 100;
        }
        query.setSize(size);

        Page<ReadRecordVO> page = readRecordController.pageList(query).getData();
        List<ReadRecordMcpVO> records = page.getRecords().stream()
                .map(this::toMcpVO)
                .toList();
        return ReadRecordPageMcpVO.builder()
                .records(records)
                .total(page.getTotal())
                .build();
    }

    private ReadRecordMcpVO toMcpVO(ReadRecordVO vo) {
        ReadRecordMcpVO mcp = new ReadRecordMcpVO();
        mcp.setId(vo.getId() == null ? null : Long.valueOf(vo.getId()));
        mcp.setTitle(vo.getTitle());
        mcp.setType(vo.getType());
        mcp.setAuthor(vo.getAuthor());
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
        return READ_STATUS_LABELS.get(status);
    }
}
