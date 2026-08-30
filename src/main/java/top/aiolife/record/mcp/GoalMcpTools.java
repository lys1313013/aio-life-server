package top.aiolife.record.mcp;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import top.aiolife.mcp.annotation.McpToolProvider;
import top.aiolife.record.mcp.req.GoalProgressUpdateMcpReq;
import top.aiolife.record.mcp.req.GoalQueryMcpReq;
import top.aiolife.record.mcp.vo.GoalMcpVO;
import top.aiolife.record.mcp.vo.GoalPageMcpVO;
import top.aiolife.record.pojo.entity.GoalEntity;
import top.aiolife.record.pojo.enums.GoalStatusEnum;
import top.aiolife.record.pojo.enums.GoalTypeEnum;
import top.aiolife.record.service.IGoalService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 目标 MCP 工具
 *
 * @author Lys
 * @date 2026/08/30
 */
@McpToolProvider
@RequiredArgsConstructor
public class GoalMcpTools {

    private final IGoalService goalService;

    @Tool("分页查询目标列表及进度，返回中文语义的目标类型与状态，含父子层级（parentId）与子目标完成情况，供 AI 感知用户正在追求什么")
    public GoalPageMcpVO goal_query(GoalQueryMcpReq req) {
        long userId = StpUtil.getLoginIdAsLong();

        LambdaQueryWrapper<GoalEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(GoalEntity::getUserId, userId);
        queryWrapper.eq(GoalEntity::getIsDeleted, 0);
        if (req.getType() != null) {
            queryWrapper.eq(GoalEntity::getType, req.getType());
        }
        if (req.getStatus() != null) {
            queryWrapper.eq(GoalEntity::getStatus, req.getStatus());
        }
        if (StringUtils.hasText(req.getKeyword())) {
            String keyword = req.getKeyword().trim();
            queryWrapper.and(wrapper -> wrapper.like(GoalEntity::getTitle, keyword)
                    .or()
                    .like(GoalEntity::getDescription, keyword)
                    .or()
                    .like(GoalEntity::getTags, keyword));
        }
        if (req.getYear() != null) {
            queryWrapper.eq(GoalEntity::getYear, req.getYear());
        }
        if (req.getMonth() != null) {
            queryWrapper.eq(GoalEntity::getMonth, req.getMonth());
        }
        queryWrapper.orderByDesc(GoalEntity::getCreateTime);

        int pageNo = req.getPage() == null ? 1 : req.getPage();
        int size = req.getSize() == null ? 10 : Math.min(req.getSize(), 100);
        Page<GoalEntity> page = goalService.page(new Page<>(pageNo, size), queryWrapper);

        // 批量统计当前页各目标的子目标完成情况
        List<Long> ids = page.getRecords().stream().map(GoalEntity::getId).toList();
        Map<Long, long[]> childStats = ids.isEmpty() ? Map.of() : goalService.lambdaQuery()
                .select(GoalEntity::getParentId, GoalEntity::getStatus)
                .in(GoalEntity::getParentId, ids)
                .eq(GoalEntity::getIsDeleted, 0)
                .list()
                .stream()
                .collect(Collectors.groupingBy(
                        GoalEntity::getParentId,
                        Collectors.collectingAndThen(Collectors.toList(), children -> new long[]{
                                children.stream().filter(c -> GoalStatusEnum.COMPLETED.getCode().equals(c.getStatus())).count(),
                                children.size()
                        })));

        List<GoalMcpVO> records = page.getRecords().stream()
                .map(entity -> toMcpVO(entity, childStats.get(entity.getId())))
                .toList();
        return GoalPageMcpVO.builder()
                .records(records)
                .total(page.getTotal())
                .build();
    }

    @Tool("更新目标进展：修改当前进度值和/或状态（进行中/已完成/已放弃），目标须属于当前用户；已完成时自动记录完成时间并补齐进度，进行中时清除完成时间")
    public String goal_progress_update(GoalProgressUpdateMcpReq req) {
        if (req.getGoalId() == null) {
            throw new IllegalArgumentException("目标ID不能为空");
        }
        if (req.getCurrentValue() == null && !StringUtils.hasText(req.getStatus())) {
            throw new IllegalArgumentException("currentValue 与 status 至少传一个");
        }

        long userId = StpUtil.getLoginIdAsLong();
        GoalEntity goal = goalService.lambdaQuery()
                .eq(GoalEntity::getId, req.getGoalId())
                .eq(GoalEntity::getUserId, userId)
                .eq(GoalEntity::getIsDeleted, 0)
                .one();
        if (goal == null) {
            throw new IllegalArgumentException("目标不存在或无权限访问该目标");
        }

        GoalStatusEnum targetStatus = null;
        if (StringUtils.hasText(req.getStatus())) {
            targetStatus = parseStatus(req.getStatus());
            Integer currentStatus = goal.getStatus();
            // 仅允许「待开始 / 进行中」状态流转，已完成/已放弃需先恢复为进行中
            if (!GoalStatusEnum.PENDING.getCode().equals(currentStatus)
                    && !GoalStatusEnum.IN_PROGRESS.getCode().equals(currentStatus)) {
                throw new IllegalArgumentException("目标当前状态为「" + statusLabel(currentStatus) + "」，不允许直接变更状态");
            }
        }

        LambdaUpdateWrapper<GoalEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(GoalEntity::getId, goal.getId());
        updateWrapper.eq(GoalEntity::getUserId, userId);
        updateWrapper.set(GoalEntity::getUpdateTime, LocalDateTime.now());
        updateWrapper.set(GoalEntity::getUpdateUser, userId);

        Integer currentValue = req.getCurrentValue();
        if (targetStatus == GoalStatusEnum.COMPLETED) {
            updateWrapper.set(GoalEntity::getCompletedAt, LocalDateTime.now());
            if (currentValue == null && goal.getTargetValue() != null) {
                currentValue = goal.getTargetValue();
            }
        } else if (targetStatus == GoalStatusEnum.IN_PROGRESS) {
            updateWrapper.set(GoalEntity::getCompletedAt, null);
        }
        if (currentValue != null) {
            updateWrapper.set(GoalEntity::getCurrentValue, currentValue);
        }
        if (targetStatus != null) {
            updateWrapper.set(GoalEntity::getStatus, targetStatus.getCode());
        }
        goalService.update(updateWrapper);

        StringBuilder message = new StringBuilder("目标「").append(goal.getTitle()).append("」");
        if (currentValue != null) {
            message.append("进度已更新：").append(currentValue);
            if (goal.getTargetValue() != null) {
                message.append("/").append(goal.getTargetValue());
            }
        }
        if (targetStatus != null) {
            if (currentValue != null) {
                message.append("，");
            }
            message.append("状态已变更为：").append(targetStatus.getDesc());
        }
        return message.toString();
    }

    private GoalMcpVO toMcpVO(GoalEntity entity, long[] childStats) {
        GoalMcpVO vo = new GoalMcpVO();
        vo.setId(entity.getId());
        vo.setType(typeLabel(entity.getType()));
        vo.setTitle(entity.getTitle());
        vo.setDescription(entity.getDescription());
        vo.setStatus(statusLabel(entity.getStatus()));
        vo.setTargetValue(entity.getTargetValue());
        vo.setCurrentValue(entity.getCurrentValue());
        vo.setYear(entity.getYear());
        vo.setMonth(entity.getMonth());
        vo.setDay(entity.getDay());
        vo.setParentId(entity.getParentId());
        vo.setStartDate(entity.getStartDate());
        vo.setEndDate(entity.getEndDate());
        vo.setCompletedAt(entity.getCompletedAt());
        vo.setTags(parseTags(entity.getTags()));
        if (childStats != null) {
            vo.setCompletedCount((int) childStats[0]);
            vo.setTotalCount((int) childStats[1]);
        } else {
            vo.setCompletedCount(0);
            vo.setTotalCount(0);
        }
        return vo;
    }

    private GoalStatusEnum parseStatus(String label) {
        // 本工具仅允许流转到 进行中 / 已完成 / 已放弃，不支持回退为待开始
        for (GoalStatusEnum value : new GoalStatusEnum[]{GoalStatusEnum.IN_PROGRESS, GoalStatusEnum.COMPLETED, GoalStatusEnum.ABANDONED}) {
            if (value.getDesc().equals(label)) {
                return value;
            }
        }
        throw new IllegalArgumentException("状态仅支持：进行中 / 已完成 / 已放弃");
    }

    private String typeLabel(Integer code) {
        if (code == null) {
            return null;
        }
        for (GoalTypeEnum value : GoalTypeEnum.values()) {
            if (value.getCode().equals(code)) {
                return value.getDesc();
            }
        }
        return String.valueOf(code);
    }

    private String statusLabel(Integer code) {
        if (code == null) {
            return null;
        }
        for (GoalStatusEnum value : GoalStatusEnum.values()) {
            if (value.getCode().equals(code)) {
                return value.getDesc();
            }
        }
        return String.valueOf(code);
    }

    private List<String> parseTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return null;
        }
        try {
            return JSON.parseArray(tags, String.class);
        } catch (Exception e) {
            // 兼容非 JSON 的历史数据，原样返回
            return List.of(tags);
        }
    }
}
