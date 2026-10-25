package top.aiolife.export.dataset;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import top.aiolife.export.mapper.ExportDatasetMapper;
import top.aiolife.export.mapper.SqlDatasetDefinition;
import top.aiolife.export.service.ExportHash;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 版本化目录：新增业务只扩展适配器，任务引擎无需修改。 */
@Component
public class ExportDatasetRegistry {
    private final Map<String, ExportDataset> datasets = new LinkedHashMap<>();
    private final String version;

    public ExportDatasetRegistry(
            ObjectMapper json,
            ExportDatasetMapper mapper,
            List<ExportDatasetContributor> contributors)
            throws IOException {
        byte[] bytes = new ClassPathResource("export/datasets.json").getContentAsByteArray();
        List<SqlDatasetDefinition> definitions = json.readValue(bytes, new TypeReference<>() {});
        definitions.forEach(definition -> register(new SqlExportDataset(definition, mapper)));
        List<ExportDataset> extensions =
                contributors.stream().flatMap(c -> c.datasets().stream()).toList();
        extensions.forEach(this::register);
        version =
                ExportHash.of(
                        new String(bytes, StandardCharsets.UTF_8)
                                + extensions.stream()
                                        .map(e -> e.key() + e.columns())
                                        .sorted()
                                        .toList());
    }

    private void register(ExportDataset dataset) {
        if (datasets.putIfAbsent(dataset.key(), dataset) != null)
            throw new IllegalStateException("Duplicate dataset: " + dataset.key());
    }

    public Collection<ExportDataset> all() {
        return List.copyOf(datasets.values());
    }

    public String version() {
        return version;
    }

    public List<ExportDataset> resolve(List<String> keys) {
        if (keys == null
                || keys.isEmpty()
                || keys.size() > 100
                || keys.stream().distinct().count() != keys.size())
            throw new IllegalArgumentException("请选择有效且不重复的数据集");
        return keys.stream()
                .sorted()
                .map(
                        key -> {
                            ExportDataset dataset = datasets.get(key);
                            if (dataset == null) throw new IllegalArgumentException("未知数据集");
                            return dataset;
                        })
                .toList();
    }
}
