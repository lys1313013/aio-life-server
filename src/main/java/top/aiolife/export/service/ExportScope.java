package top.aiolife.export.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import top.aiolife.export.dataset.ExportDataset;
import top.aiolife.export.dataset.ExportDatasetRegistry;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ExportScope {
    private final ExportDatasetRegistry registry;
    private final ObjectMapper json;

    public String encode(List<ExportDataset> datasets) {
        try {
            return json.writeValueAsString(
                    datasets.stream().map(ExportDataset::key).sorted().toList());
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot encode export scope");
        }
    }

    public List<ExportDataset> decode(String encoded) {
        try {
            return registry.resolve(json.readValue(encoded, new TypeReference<List<String>>() {}));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Invalid persisted export scope");
        }
    }

    public String hash(List<ExportDataset> datasets) {
        return ExportHash.of(registry.version() + ":" + encode(datasets));
    }
}
