package top.aiolife.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.aiolife.core.resq.PageResp;
import top.aiolife.system.mapper.ActivityLogMapper;
import top.aiolife.system.pojo.entity.ActivityLogEntity;
import top.aiolife.system.pojo.query.ActivityLogQuery;

import java.io.IOException;
import java.io.Writer;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityLogService {
    private final ActivityLogMapper mapper;

    public PageResp<ActivityLogEntity> list(String type, ActivityLogQuery query) {
        var page = mapper.selectPage(new Page<>(query.getPage(), query.getPageSize()),
                filter(type, query).orderByDesc(ActivityLogEntity::getCreateTime, ActivityLogEntity::getId));
        return PageResp.of(page.getRecords(), page.getTotal());
    }

    /** 按游标分批导出当前筛选的全部数据，避免把全表载入内存。 */
    public void export(String type, ActivityLogQuery query, Writer writer) throws IOException {
        var wrapper = filter(type, query);
        // 以导出开始时的最大 ID 为界，排除导出过程中新增的记录。
        var newest = mapper.selectList(wrapper.orderByDesc(ActivityLogEntity::getId).last("LIMIT 1"));
        writer.write('\ufeff');
        writer.write("序号,用户账号,用户名称,IP地址,浏览器," +
                ("ACCESS".equals(type) ? "登录方式," : "功能名称,功能项,") + "结果,操作时间\r\n");
        if (newest.isEmpty()) return;
        Long cursor = null;
        long index = 0;
        while (true) {
            var batchFilter = filter(type, query)
                    .le(ActivityLogEntity::getId, newest.getFirst().getId())
                    .lt(cursor != null, ActivityLogEntity::getId, cursor)
                    .orderByDesc(ActivityLogEntity::getId).last("LIMIT 1000");
            List<ActivityLogEntity> batch = mapper.selectList(batchFilter);
            if (batch.isEmpty()) break;
            for (var row : batch) {
                writer.write(++index + "," + csv(row.getUsername()) + "," + csv(row.getNickname()) + ","
                        + csv(row.getIpAddress()) + "," + csv(row.getBrowser()) + ","
                        + ("ACCESS".equals(type) ? csv(row.getAccessType()) :
                        csv(row.getFunctionName()) + "," + csv(row.getFunctionItem())) + ","
                        + (Boolean.TRUE.equals(row.getSuccess()) ? "成功" : "失败") + ","
                        + csv(row.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))) + "\r\n");
            }
            cursor = batch.getLast().getId();
        }
    }

    private LambdaQueryWrapper<ActivityLogEntity> filter(String type, ActivityLogQuery query) {
        if (!List.of("OPERATION", "ACCESS").contains(type)) throw new IllegalArgumentException("日志类型无效");
        query.validateRange();
        var wrapper = new LambdaQueryWrapper<ActivityLogEntity>().eq(ActivityLogEntity::getLogType, type);
        if (StringUtils.hasText(query.getUsername())) {
            // 将通配符视为账号字面值。
            String account = query.getUsername().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            wrapper.like(ActivityLogEntity::getUsername, account);
        }
        if (query.getStartDate() != null) wrapper.ge(ActivityLogEntity::getCreateTime, query.getStartDate().atStartOfDay());
        if (query.getEndDate() != null) wrapper.lt(ActivityLogEntity::getCreateTime, query.getEndDate().plusDays(1).atStartOfDay());
        return wrapper;
    }

    static String csv(String value) {
        if (value == null) return "\"\"";
        // 账号等字段可能来自用户输入，阻止电子表格将其作为公式执行。
        String trimmed = value.stripLeading();
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0
                || value.startsWith("\t") || value.startsWith("\r") || value.startsWith("\n")) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
