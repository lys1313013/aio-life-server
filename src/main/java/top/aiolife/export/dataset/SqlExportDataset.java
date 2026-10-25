package top.aiolife.export.dataset;

import top.aiolife.export.mapper.ExportDatasetMapper;
import top.aiolife.export.mapper.SqlDatasetDefinition;

import java.util.List;
import java.util.Map;

/** 简单表和经父表约束的数据集共享分页代码，不共享不明确的所有权规则。 */
public record SqlExportDataset(SqlDatasetDefinition definition, ExportDatasetMapper mapper)
        implements ExportDataset {
    public String key() {
        return definition.key();
    }

    public String title() {
        return definition.title();
    }

    public List<String> menus() {
        return definition.menus();
    }

    public List<ExportColumn> columns() {
        return definition.columns();
    }

    public List<Map<String, Object>> readBatch(long ownerId, String cursor, int limit) {
        if (limit < 1 || limit > 1000)
            throw new IllegalArgumentException("Invalid export batch size");
        return mapper.read(definition, ownerId, cursor, limit);
    }
}
