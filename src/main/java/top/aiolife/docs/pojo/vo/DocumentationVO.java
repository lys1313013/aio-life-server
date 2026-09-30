package top.aiolife.docs.pojo.vo;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/** 渐进式文档响应；详情中的 definition 是仅含一个操作的完整 OpenAPI 文档。 */
public final class DocumentationVO {
    private DocumentationVO() {}

    public record Module(String id, String name, String description, int operationCount) {}

    public record Catalog(List<Module> modules, int operationCount) {}

    public record OperationSummary(String operationId, String name, String method, String path,
                                   List<String> modules) {}

    public record OperationPage(List<OperationSummary> items, long total, int page, int pageSize) {}

    public record OperationDetail(String operationId, String method, String path, List<String> modules,
                                  ObjectNode definition) {}
}
