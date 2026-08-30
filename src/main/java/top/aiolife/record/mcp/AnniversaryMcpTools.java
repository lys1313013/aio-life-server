package top.aiolife.record.mcp;

import cn.dev33.satoken.stp.StpUtil;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import top.aiolife.mcp.annotation.McpToolProvider;
import top.aiolife.record.mcp.req.AnniversaryQueryMcpReq;
import top.aiolife.record.mcp.vo.AnniversaryMcpVO;
import top.aiolife.record.pojo.entity.AnniversaryRecordEntity;
import top.aiolife.record.service.IAnniversaryRecordService;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 纪念日 MCP 工具
 *
 * @author Lys
 * @date 2026/08/30
 */
@McpToolProvider
@RequiredArgsConstructor
public class AnniversaryMcpTools {

    private static final String TYPE_ANNIVERSARY = "anniversary";
    private static final String TYPE_COUNTDOWN = "countdown";

    private static final Map<String, String> TYPE_LABELS = Map.of(
            TYPE_ANNIVERSARY, "纪念日",
            TYPE_COUNTDOWN, "倒数日");

    private final IAnniversaryRecordService anniversaryRecordService;

    @Tool("查询纪念日/倒数日，daysRemaining 由后端计算（正数=还有 N 天，负数=已过 N 天），纪念日按下一次发生计算并跨年生效，供 AI 主动提醒临近日子")
    public List<AnniversaryMcpVO> anniversary_query(AnniversaryQueryMcpReq req) {
        long userId = StpUtil.getLoginIdAsLong();

        String typeFilter = null;
        if (StringUtils.hasText(req.getType())) {
            typeFilter = switch (req.getType()) {
                case "纪念日" -> TYPE_ANNIVERSARY;
                case "倒数日" -> TYPE_COUNTDOWN;
                default -> throw new IllegalArgumentException("类型仅支持：纪念日 / 倒数日");
            };
        }

        var queryWrapper = anniversaryRecordService.lambdaQuery()
                .eq(AnniversaryRecordEntity::getUserId, userId);
        if (typeFilter != null) {
            queryWrapper.eq(AnniversaryRecordEntity::getType, typeFilter);
        }
        List<AnniversaryRecordEntity> entities = queryWrapper.list();

        LocalDate today = LocalDate.now();
        return entities.stream()
                .map(entity -> toMcpVO(entity, today))
                .filter(vo -> req.getWithinDays() == null
                        || (vo.getDaysRemaining() >= 0 && vo.getDaysRemaining() <= req.getWithinDays()))
                .sorted(Comparator.comparing(AnniversaryMcpVO::getDaysRemaining))
                .toList();
    }

    private AnniversaryMcpVO toMcpVO(AnniversaryRecordEntity entity, LocalDate today) {
        AnniversaryMcpVO vo = new AnniversaryMcpVO();
        vo.setId(entity.getId());
        vo.setTitle(entity.getTitle());
        vo.setType(TYPE_LABELS.getOrDefault(entity.getType(), entity.getType()));
        vo.setTargetDate(entity.getTargetDate());
        vo.setNote(entity.getNote());
        vo.setDaysRemaining(computeDaysRemaining(entity, today));
        return vo;
    }

    private long computeDaysRemaining(AnniversaryRecordEntity entity, LocalDate today) {
        LocalDate targetDate = entity.getTargetDate();
        if (targetDate == null) {
            return 0;
        }
        if (TYPE_COUNTDOWN.equals(entity.getType())) {
            // 倒数日：按目标日期直接计算
            return ChronoUnit.DAYS.between(today, targetDate);
        }
        // 纪念日：按「下一次发生」计算，今年已过则算到明年
        LocalDate next = withYearSafe(targetDate, today.getYear());
        if (next.isBefore(today)) {
            next = withYearSafe(targetDate, today.getYear() + 1);
        }
        return ChronoUnit.DAYS.between(today, next);
    }

    /**
     * 调整年份，兼容 2 月 29 日在平年落到 2 月 28 日
     */
    private LocalDate withYearSafe(LocalDate date, int year) {
        try {
            return date.withYear(year);
        } catch (DateTimeException e) {
            return LocalDate.of(year, date.getMonthValue(), date.getMonth().minLength());
        }
    }
}
