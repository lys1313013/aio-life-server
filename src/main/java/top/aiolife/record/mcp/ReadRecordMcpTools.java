package top.aiolife.record.mcp;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import top.aiolife.mcp.annotation.McpToolProvider;
import top.aiolife.record.api.ReadRecordController;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;
import top.aiolife.record.pojo.query.ReadRecordQuery;
import top.aiolife.record.pojo.vo.ReadRecordVO;
import top.aiolife.record.mcp.req.ReadRecordQueryMcpReq;
import top.aiolife.record.mcp.vo.ReadRecordMcpVO;
import top.aiolife.record.mcp.vo.ReadRecordPageMcpVO;

import java.util.Arrays;
import java.util.List;

/**
 * 阅读记录 MCP 工具
 *
 * @author Lys
 * @date 2026/08/22
 */
@McpToolProvider
@RequiredArgsConstructor
public class ReadRecordMcpTools {

    private final ReadRecordController readRecordController;

    @Tool("分页查询阅读记录，供 AI 感知读书进度，status 以中文语义返回")
    public ReadRecordPageMcpVO read_record_query(ReadRecordQueryMcpReq req) {
        ReadRecordQuery query = new ReadRecordQuery();
        BeanUtil.copyProperties(req, query);
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
        BeanUtil.copyProperties(vo, mcp);
        mcp.setId(vo.getId() == null ? null : Long.valueOf(vo.getId()));
        mcp.setStatus(statusLabel(vo.getStatus()));
        return mcp;
    }

    private String statusLabel(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(ProgressStatusEnum.values())
                .filter(e -> e.getCode().equals(code))
                .map(ProgressStatusEnum::getDesc)
                .findFirst()
                .orElse(String.valueOf(code));
    }
}