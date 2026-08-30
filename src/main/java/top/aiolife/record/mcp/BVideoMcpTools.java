package top.aiolife.record.mcp;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import top.aiolife.mcp.annotation.McpToolProvider;
import top.aiolife.record.enums.StudyEnum;
import top.aiolife.record.mapper.IBVideoMapper;
import top.aiolife.record.mcp.req.BVideoQueryMcpReq;
import top.aiolife.record.mcp.vo.BVideoMcpVO;
import top.aiolife.record.mcp.vo.BVideoPageMcpVO;
import top.aiolife.record.mcp.vo.BVideoStatisticsMcpVO;
import top.aiolife.record.pojo.entity.BVideoEntity;
import top.aiolife.record.pojo.vo.StatusCount;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * B站学习视频 MCP 工具（只读）
 *
 * @author Lys
 * @date 2026/08/30
 */
@McpToolProvider
@RequiredArgsConstructor
public class BVideoMcpTools {

    private final IBVideoMapper bVideoMapper;

    @Tool("分页查询B站学习视频，供 AI 感知视频学习进度，status 以中文语义返回，progressPercentage 由后端计算")
    public BVideoPageMcpVO b_video_query(BVideoQueryMcpReq req) {
        long userId = StpUtil.getLoginIdAsLong();

        LambdaQueryWrapper<BVideoEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BVideoEntity::getUserId, userId);
        if (StringUtils.hasText(req.getTitle())) {
            queryWrapper.like(BVideoEntity::getTitle, req.getTitle().trim());
        }
        if (req.getStatus() != null) {
            queryWrapper.eq(BVideoEntity::getStatus, req.getStatus());
        }
        queryWrapper.orderByDesc(BVideoEntity::getLastWatched);

        int pageNo = req.getPage() == null ? 1 : req.getPage();
        int size = req.getSize() == null ? 10 : Math.min(req.getSize(), 100);
        Page<BVideoEntity> page = bVideoMapper.selectPage(new Page<>(pageNo, size), queryWrapper);

        List<BVideoMcpVO> records = page.getRecords().stream()
                .map(this::toMcpVO)
                .toList();
        return BVideoPageMcpVO.builder()
                .records(records)
                .total(page.getTotal())
                .build();
    }

    @Tool("B站学习统计：返回已学习/未学习/总时长、整体学习进度百分比及各状态数量，供 AI 了解整体学习投入")
    public BVideoStatisticsMcpVO b_video_statistics() {
        long userId = StpUtil.getLoginIdAsLong();

        Integer watchTime = bVideoMapper.getWatchTime(userId);
        Integer totalTime = bVideoMapper.getTotalTime(userId);
        int studiedSeconds = watchTime == null ? 0 : watchTime;
        int totalSeconds = totalTime == null ? 0 : totalTime;

        List<StatusCount> statusCounts = bVideoMapper.getStatusCount(userId);
        Map<String, Integer> statusCountMap = statusCounts == null ? Map.of() : statusCounts.stream()
                .collect(Collectors.toMap(
                        sc -> statusLabel(sc.getStatus()),
                        StatusCount::getCount,
                        Integer::sum));

        BVideoStatisticsMcpVO vo = new BVideoStatisticsMcpVO();
        vo.setStudiedSeconds(studiedSeconds);
        vo.setTotalSeconds(totalSeconds);
        vo.setUnstudiedSeconds(Math.max(totalSeconds - studiedSeconds, 0));
        vo.setProgressPercentage(percentage(studiedSeconds, totalSeconds));
        vo.setStatusCounts(statusCountMap);
        return vo;
    }

    private BVideoMcpVO toMcpVO(BVideoEntity entity) {
        BVideoMcpVO vo = new BVideoMcpVO();
        vo.setId(entity.getId());
        vo.setTitle(entity.getTitle());
        vo.setOwnerName(entity.getOwnerName());
        vo.setBvid(entity.getBvid());
        vo.setStatus(statusLabel(entity.getStatus()));
        vo.setDuration(entity.getDuration());
        vo.setWatchedDuration(entity.getWatchedDuration());
        vo.setEpisodes(entity.getEpisodes());
        vo.setCurrentEpisode(entity.getCurrentEpisode());
        vo.setProgressPercentage(percentage(
                entity.getWatchedDuration() == null ? 0 : entity.getWatchedDuration(),
                entity.getDuration() == null ? 0 : entity.getDuration()));
        vo.setLastWatched(entity.getLastWatched());
        vo.setNotes(entity.getNotes());
        return vo;
    }

    private double percentage(int part, int total) {
        if (total <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(part * 100.0 / total)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private String statusLabel(Integer code) {
        if (code == null) {
            return null;
        }
        for (StudyEnum value : StudyEnum.values()) {
            if (value.getValue() == code) {
                return value.getLabel();
            }
        }
        return String.valueOf(code);
    }
}
