package top.aiolife.docs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.api.MultipleOpenApiWebMvcResource;
import org.springdoc.webmvc.api.OpenApiWebMvcResource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.docs.pojo.vo.DocumentationVO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 从 springdoc 实际输出构建只读索引，不调用本机 HTTP，也不另写接口清单。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", matchIfMissing = true)
public class DocumentationService {
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");
    // 仅维护模块文案，接口归属和数量均取自 springdoc 分组文档。
    private static final Map<String, List<String>> MODULE_LABELS = Map.ofEntries(
            Map.entry("record", List.of("生活记录", "时迹、目标、待办、运动、阅读、活动及其他生活记录")),
            Map.entry("sso", List.of("认证与账号", "登录注册、账号绑定、API Key 与文件访问")),
            Map.entry("system", List.of("系统管理", "用户、菜单与字典管理")),
            Map.entry("wardrobe", List.of("衣柜", "衣物与穿搭记录")),
            Map.entry("membership", List.of("会员", "会员记录与统计")),
            Map.entry("bankcard", List.of("银行卡", "银行卡管理")),
            Map.entry("feedback", List.of("反馈", "用户反馈、评论与处理")),
            Map.entry("relationship", List.of("人际关系", "人物与关系图谱")),
            Map.entry("llm", List.of("AI", "大模型与 AI 功能")),
            Map.entry("mcp", List.of("MCP", "工具发现与调用")));

    private final ObjectProvider<OpenApiWebMvcResource> openApiResource;
    private final ObjectProvider<MultipleOpenApiWebMvcResource> groupedResource;
    private final SpringDocConfigProperties properties;
    private final ObjectMapper mapper;
    private volatile Snapshot cached;

    public DocumentationVO.Catalog catalog(HttpServletRequest request) {
        Snapshot snapshot = snapshot(request);
        return new DocumentationVO.Catalog(snapshot.modules(), snapshot.operations().size());
    }

    public DocumentationVO.OperationPage search(HttpServletRequest request, String keyword, String module,
                                                String pageValue, String sizeValue) {
        int page = positiveInteger(pageValue, "page", Integer.MAX_VALUE);
        int pageSize = positiveInteger(sizeValue, "pageSize", 100);
        String query = keyword.strip().toLowerCase(Locale.ROOT);
        if (query.length() > 200) throw badRequest("keyword 不能超过 200 个字符");
        String moduleId = module.strip();
        Snapshot snapshot = snapshot(request);
        if (!moduleId.isEmpty() && snapshot.modules().stream().noneMatch(item -> item.id().equals(moduleId))) {
            throw badRequest("未知模块：" + moduleId);
        }
        List<Entry> matches = snapshot.operations().values().stream()
                .filter(item -> moduleId.isEmpty() || item.summary().modules().contains(moduleId))
                .filter(item -> query.isEmpty() || item.searchText().contains(query))
                .sorted(Comparator.comparing(item -> item.summary().operationId()))
                .toList();
        long offset = (long) (page - 1) * pageSize;
        List<DocumentationVO.OperationSummary> items = matches.stream().skip(offset).limit(pageSize)
                .map(Entry::summary).toList();
        return new DocumentationVO.OperationPage(items, matches.size(), page, pageSize);
    }

    public DocumentationVO.OperationDetail detail(HttpServletRequest request, String operationId) {
        Snapshot snapshot = snapshot(request);
        Entry entry = snapshot.operations().get(operationId);
        if (entry == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "接口文档不存在：" + operationId);
        var summary = entry.summary();
        ObjectNode definition = OpenApiOperationExtractor.extract(snapshot.document(), summary.path(), summary.method());
        // 使用同源相对地址，避免索引缓存带入首个请求的 Host，也适配 /api 上下文。
        definition.putArray("servers").addObject().put("url", request.getContextPath().isEmpty() ? "/" : request.getContextPath());
        return new DocumentationVO.OperationDetail(operationId, summary.method(), summary.path(), summary.modules(), definition);
    }

    private Snapshot snapshot(HttpServletRequest request) {
        // 与 springdoc 缓存开关保持一致。应用重启/开发热重载后自动重新生成。
        if (properties.isCacheDisabled()) return buildSnapshot(request);
        Snapshot snapshot = cached;
        if (snapshot == null) {
            synchronized (this) {
                snapshot = cached;
                if (snapshot == null) cached = snapshot = buildSnapshot(request);
            }
        }
        return snapshot;
    }

    private Snapshot buildSnapshot(HttpServletRequest request) {
        try {
            String docsPath = properties.getApiDocs().getPath();
            ObjectNode document = (ObjectNode) mapper.readTree(openApiResource.getObject()
                    .openapiJson(documentRequest(request, docsPath), docsPath, Locale.ROOT));
            Map<String, Entry> operations = new LinkedHashMap<>();
            Map<String, List<String>> memberships = new LinkedHashMap<>();
            List<DocumentationVO.Module> modules = new ArrayList<>();
            for (var group : properties.getGroupConfigs().stream()
                    .sorted(Comparator.comparing(SpringDocConfigProperties.GroupConfig::getGroup)).toList()) {
                String id = group.getGroup();
                JsonNode groupDocument = mapper.readTree(groupedResource.getObject().openapiJson(
                        documentRequest(request, docsPath + "/" + id), docsPath, id, Locale.ROOT));
                Map<String, Entry> grouped = readOperations(groupDocument, Map.of());
                grouped.keySet().forEach(operationId -> memberships.computeIfAbsent(operationId, ignored -> new ArrayList<>()).add(id));
                List<String> label = MODULE_LABELS.getOrDefault(id, List.of(id, id));
                modules.add(new DocumentationVO.Module(id,
                        group.getDisplayName() == null ? label.getFirst() : group.getDisplayName(), label.getLast(), grouped.size()));
            }
            operations.putAll(readOperations(document, memberships));
            int otherCount = (int) operations.values().stream().filter(item -> item.summary().modules().contains("other")).count();
            if (otherCount > 0) modules.add(new DocumentationVO.Module("other", "其他接口", "未分组的已启用接口", otherCount));
            return new Snapshot(document, Map.copyOf(operations), List.copyOf(modules));
        } catch (IOException e) {
            throw new IllegalStateException("生成接口文档失败", e);
        }
    }

    private Map<String, Entry> readOperations(JsonNode document, Map<String, List<String>> memberships) {
        Map<String, Entry> result = new LinkedHashMap<>();
        document.path("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
            if (!METHODS.contains(method.getKey())) return;
            JsonNode operation = method.getValue();
            String id = operation.path("operationId").asText();
            if (id.isBlank()) throw new IllegalStateException("接口缺少 operationId：" + path.getKey());
            String name = operation.path("summary").asText("").strip();
            if (name.isEmpty()) name = firstLine(operation.path("description").asText(id));
            var summary = new DocumentationVO.OperationSummary(id, name, method.getKey().toUpperCase(Locale.ROOT),
                    path.getKey(), List.copyOf(memberships.getOrDefault(id, List.of("other"))));
            String searchText = (id + " " + name + " " + path.getKey() + " " + method.getKey() + " "
                    + operation.path("description").asText() + " " + operation.path("tags"))
                    .toLowerCase(Locale.ROOT);
            if (result.put(id, new Entry(summary, searchText)) != null) {
                throw new IllegalStateException("重复的 operationId：" + id);
            }
        }));
        return result;
    }

    private String firstLine(String text) {
        String line = text.lines().findFirst().orElse("").strip();
        return line.length() > 160 ? line.substring(0, 160) : line;
    }

    private int positiveInteger(String value, String name, int maximum) {
        try {
            int number = Integer.parseInt(value);
            if (number < 1 || number > maximum) throw badRequest(name + " 必须在 1 到 " + maximum + " 之间");
            return number;
        } catch (NumberFormatException e) {
            throw badRequest(name + " 必须为正整数");
        }
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private HttpServletRequest documentRequest(HttpServletRequest request, String path) {
        String uri = request.getContextPath() + path;
        String url = request.getRequestURL().toString();
        String origin = url.substring(0, url.length() - request.getRequestURI().length());
        // springdoc 从文档 URL 计算服务器地址；不能传入 /docs/catalog 的 URL 冒充 /v3/api-docs。
        return new HttpServletRequestWrapper(request) {
            @Override public StringBuffer getRequestURL() { return new StringBuffer(origin + uri); }
            @Override public String getRequestURI() { return uri; }
            @Override public String getServletPath() { return path; }
        };
    }

    private record Entry(DocumentationVO.OperationSummary summary, String searchText) {}
    private record Snapshot(ObjectNode document, Map<String, Entry> operations, List<DocumentationVO.Module> modules) {}
}
