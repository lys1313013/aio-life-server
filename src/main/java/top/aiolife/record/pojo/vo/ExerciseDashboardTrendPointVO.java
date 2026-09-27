package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/** 同一运动类型某日的合计数量。 */
public record ExerciseDashboardTrendPointVO(@JsonFormat(pattern = "yyyy-MM-dd") LocalDate date, int count) {
}
