package top.aiolife.record.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.aiolife.record.client.GoalQueryClient;
import top.aiolife.record.pojo.entity.GoalEntity;
import top.aiolife.record.pojo.vo.GoalProgressPreviewVO;

/** 只做变量绑定、标量提取与目标比较；统计口径由用户 GraphQL 表达。 */
@Component
@RequiredArgsConstructor
public class GoalProgressEvaluator {
    private static final Set<String> BINDINGS = Set.of("goal.startDate", "goal.endDateExclusive", "goal.startMonth", "goal.endMonthExclusive");
    private static final BigDecimal MAX = new BigDecimal("999999999999.99");
    private final ObjectMapper json;
    private final GoalQueryClient query;

    public void validate(GoalEntity goal) {
        if (goal.getProgressMode() == null) goal.setProgressMode("manual");
        if (goal.getTargetOperator() == null) goal.setTargetOperator("gte");
        if (!Set.of("manual", "graphql").contains(goal.getProgressMode()) || !Set.of("gte", "gt").contains(goal.getTargetOperator()))
            throw new IllegalArgumentException("进度来源或达标条件无效");
        validateNumber(goal.getTargetValue()); validateNumber(goal.getCurrentValue());
        if (!"graphql".equals(goal.getProgressMode())) return;
        if (goal.getTargetValue() == null || goal.getTargetValue().signum() <= 0) throw new IllegalArgumentException("自动查询目标需要大于零的目标值");
        if (goal.getProgressQuery() == null || goal.getProgressQuery().isBlank() || goal.getProgressQuery().length() > 32768)
            throw new IllegalArgumentException("请填写 GraphQL 查询，最多 32768 字符");
        if (goal.getProgressValuePath() == null || !goal.getProgressValuePath().matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*") || goal.getProgressValuePath().length() > 256)
            throw new IllegalArgumentException("请填写有效的结果路径，如 progress.exerciseCount.max");
        variables(goal);
    }

    public ObjectNode variables(GoalEntity goal) {
        ObjectNode values = object(goal.getProgressVariables());
        ObjectNode bindings = object(goal.getProgressVariableBindings());
        bindings.properties().forEach(entry -> {
            String key = entry.getKey(), binding = entry.getValue().asText();
            if (!key.matches("[A-Za-z_][A-Za-z0-9_]*") || values.has(key) || !entry.getValue().isTextual() || !BINDINGS.contains(binding))
                throw new IllegalArgumentException("变量绑定无效或与固定变量重复");
            if (goal.getStartDate() == null || goal.getEndDate() == null || goal.getEndDate().isBefore(goal.getStartDate()))
                throw new IllegalArgumentException("日期绑定需要有效的目标开始和结束时间");
            LocalDate date = switch (binding) {
                case "goal.startDate" -> goal.getStartDate().toLocalDate();
                case "goal.endDateExclusive" -> goal.getEndDate().toLocalDate().plusDays(1);
                case "goal.startMonth" -> goal.getStartDate().toLocalDate().withDayOfMonth(1);
                default -> goal.getEndDate().toLocalDate().withDayOfMonth(1).plusMonths(1);
            };
            values.put(key, date.toString());
        });
        return values;
    }

    public GoalProgressPreviewVO evaluate(GoalEntity goal, String token) {
        validate(goal);
        if (!"graphql".equals(goal.getProgressMode())) throw new IllegalArgumentException("请先选择 GraphQL 进度来源");
        ObjectNode variables = variables(goal);
        JsonNode value = query.execute(goal.getProgressQuery(), variables, token);
        for (String part : goal.getProgressValuePath().split("\\.")) {
            if (value.isArray() && part.matches("[0-9]{1,6}")) value = value.path(Integer.parseInt(part));
            else if (value.isObject()) value = value.path(part);
            else throw new IllegalArgumentException("结果路径没有匹配到数值");
        }
        if (value.isMissingNode()) throw new IllegalArgumentException("结果路径不存在");
        BigDecimal current;
        try {
            // 已存在聚合字段的 NULL 表示空集合；缺失路径、布尔和对象均拒绝。
            if (value.isNull()) current = BigDecimal.ZERO;
            else if (value.isNumber() || (value.isTextual() && value.asText().matches("-?[0-9]+(\\.[0-9]+)?"))) current = new BigDecimal(value.asText());
            else throw new IllegalArgumentException("查询结果必须是数值");
            validateNumber(current);
        } catch (NumberFormatException error) { throw new IllegalArgumentException("查询结果必须是有限数值"); }
        var result = new GoalProgressPreviewVO();
        result.setCurrentValue(current); result.setVariables(variables);
        int comparison = current.compareTo(goal.getTargetValue());
        boolean achieved = "gt".equals(goal.getTargetOperator()) ? comparison > 0 : comparison >= 0;
        result.setAchieved(achieved);
        int percent = current.multiply(BigDecimal.valueOf(100)).divide(goal.getTargetValue(), 0, RoundingMode.HALF_UP).min(BigDecimal.valueOf(100)).intValue();
        result.setProgressPercent(achieved ? 100 : Math.min(99, Math.max(0, percent)));
        return result;
    }

    public static void validateNumber(BigDecimal value) {
        if (value != null && (value.signum() < 0 || value.stripTrailingZeros().scale() > 2 || value.compareTo(MAX) > 0))
            throw new IllegalArgumentException("进度和目标值必须为非负数，最多两位小数且不超过 999999999999.99");
    }

    private ObjectNode object(String text) {
        if (text == null || text.isBlank()) return json.createObjectNode();
        if (text.length() > 16384) throw new IllegalArgumentException("变量 JSON 过大");
        try {
            JsonNode node = json.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text);
            if (!node.isObject()) throw new IllegalArgumentException("变量必须为 JSON 对象");
            return (ObjectNode) node;
        } catch (IllegalArgumentException error) { throw error; }
        catch (Exception error) { throw new IllegalArgumentException("变量 JSON 格式错误"); }
    }
}
