package top.aiolife.config;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.PathUtils;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ResolvableType;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import top.aiolife.core.json.CountSerializer;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.query.QueryParams;
import top.aiolife.sso.api.WechatAuthController;
import top.aiolife.sso.api.WechatWebLoginController;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** OpenAPI 描述与实际 Jackson、查询参数及认证契约保持一致。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", matchIfMissing = true)
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";
    // 对应 SaTokenConfig 的匿名认证入口；文件接口另有对象级访问控制。
    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/auth/login", "/auth/register", "/auth/sendEmailCode",
            "/auth/sendResetPasswordCode", "/auth/resetPassword");

    static {
        // Long/long 默认输出字符串；CountSerializer 标注的计数字段在属性定制器中覆盖。
        SpringDocUtils.getConfig()
                .replaceWithSchema(Long.class, new StringSchema().pattern("^-?[0-9]+$"))
                .replaceWithSchema(long.class, new StringSchema().pattern("^-?[0-9]+$"));
    }

    @Bean
    public OpenAPI aioLifeOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("AIO Life API").version("1.0.0").description("""
                        AIO Life REST 接口。先读取 /api/docs/catalog，再通过 /api/docs/operations 搜索接口，
                        按 operationId 读取 /api/docs/operations/{operationId} 的 definition 后构造请求。
                        文档可匿名读取，无需登录或 API Key；业务接口仍按各自的 security 定义鉴权。
                        业务接口鉴权：Authorization: Bearer <登录 Token 或 ak- 开头的 API Key>。
                        普通 JSON 响应为 {code,message,data}，只有 code=0 表示成功；HTTP 200 也可能是业务失败。
                        code=2001 表示需要二级密码验证，data.menuPath 指明锁定模块；应提示用户完成验证后重试。
                        HTTP 401 的响应可能是纯文本。下载、流式响应以具体接口的响应定义为准。
                        Long/long 默认在 JSON 中是字符串，ID 始终保留字符串以避免大整数精度丢失。
                        PageResp.total 是例外，以 JSON 整数返回；未提供总数时保留 null。
                        日期格式以字段说明和示例为准；@QueryParams 的筛选条件平铺到 URL，不发送 GET 请求体。
                        数组查询参数使用重复键，如 statuses=in_progress&statuses=on_hold。
                        文档只包含当前应用已启用的 Controller；泛型 Map/Object 的业务字段仍需参考接口说明。
                        """))
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
                                .description("登录 Token 或 ak- 开头的 API Key；二者选一，均放在 Authorization: Bearer 后")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    @Bean
    public GlobalOperationCustomizer queryParamsOpenApiCustomizer() {
        return (operation, handlerMethod) -> {
            boolean commonQuery = Arrays.stream(handlerMethod.getMethodParameters())
                    .anyMatch(parameter -> parameter.hasParameterAnnotation(QueryParams.class)
                            && CommonQuery.class.isAssignableFrom(parameter.getParameterType()));
            if (operation.getParameters() != null) {
                for (Parameter parameter : operation.getParameters()) {
                    if (!"query".equals(parameter.getIn())) continue;
                    if (commonQuery && parameter.getName().startsWith("condition.")) {
                        parameter.setName(parameter.getName().substring("condition.".length()));
                    }
                    if (parameter.getSchema() != null && "array".equals(parameter.getSchema().getType())) {
                        parameter.style(Parameter.StyleEnum.FORM).explode(true);
                    }
                }
            }
            return operation;
        };
    }

    @Bean
    public GlobalOpenApiCustomizer apiContractCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) return;
            openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                // 根据 HTTP 方法和路径生成，避免多个 Controller 的 query/save 方法相互冲突。
                operation.setOperationId(method.name().toLowerCase(Locale.ROOT)
                        + path.replaceAll("[^a-zA-Z0-9]+", "_").replaceAll("_$", ""));
                if (PUBLIC_AUTH_PATHS.contains(path)
                        || Arrays.asList(WechatAuthController.PUBLIC_PATHS).contains(path)
                        || Arrays.asList(top.aiolife.sso.api.QrLoginController.PUBLIC_PATHS).contains(path)
                        || path.equals("/auth/qr-login/{id}/status")
                        || Arrays.asList(WechatWebLoginController.PUBLIC_PATHS).contains(path)) {
                    operation.setSecurity(List.of());
                } else if (path.startsWith("/file/preview/") || path.startsWith("/file/download/")) {
                    operation.setSecurity(List.of(new SecurityRequirement(), new SecurityRequirement().addList(BEARER_AUTH)));
                    String description = operation.getDescription() == null ? "" : operation.getDescription() + "\n";
                    operation.setDescription(description + "仅公开资源可匿名访问；私有文件仍需认证并通过属主/管理员校验。");
                }
            }));
        };
    }

    @Bean
    public GlobalOpenApiCustomizer commonQueryOpenApiCustomizer(
            @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> mappings) {
        return openApi -> {
            if (openApi.getPaths() == null) return;
            mappings.getObject().getHandlerMethods().forEach((mapping, handler) -> {
                for (var parameter : handler.getMethodParameters()) {
                    if (!parameter.hasParameterAnnotation(QueryParams.class)
                            || !CommonQuery.class.isAssignableFrom(parameter.getParameterType())) continue;
                    var conditionType = ResolvableType.forMethodParameter(parameter).getGeneric(0).resolve();
                    if (conditionType == null || conditionType == Object.class) continue;
                    for (String path : mapping.getPatternValues()) {
                        var item = openApi.getPaths().get(PathUtils.parsePath(path, new LinkedHashMap<>()));
                        if (item == null) continue;
                        var resolved = ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(conditionType));
                        Schema<?> conditionSchema = resolved.schema;
                        if (conditionSchema == null || conditionSchema.getProperties() == null) continue;
                        resolved.referencedSchemas.forEach(openApi.getComponents()::addSchemas);
                        item.readOperationsMap().forEach((method, operation) -> {
                            if (!mapping.getMethodsCondition().getMethods().isEmpty()
                                    && mapping.getMethodsCondition().getMethods().stream().noneMatch(value -> value.name().equals(method.name()))) return;
                            conditionSchema.getProperties().forEach((name, schema) -> {
                                // CommonQuery 的泛型 condition 不会被 springdoc 的普通 POJO 展开器解析。
                                if (operation.getParameters() != null && operation.getParameters().stream()
                                        .anyMatch(existing -> existing.getName().equals(name))) return;
                                Parameter query = new Parameter().in("query").name(name).schema(schema)
                                        .description(schema.getDescription()).required(false);
                                if ("array".equals(schema.getType())) query.style(Parameter.StyleEnum.FORM).explode(true);
                                operation.addParametersItem(query);
                            });
                        });
                    }
                }
            });
        };
    }

    /** 字段定制器在全局 Long 类型替换之后运行，按实际序列化器描述数字计数。 */
    @Bean
    public PropertyCustomizer countOpenApiPropertyCustomizer() {
        return (schema, type) -> {
            if (schema == null || type.getCtxAnnotations() == null) return schema;
            for (var annotation : type.getCtxAnnotations()) {
                if (annotation instanceof JsonSerialize serializer && serializer.using() == CountSerializer.class) {
                    return new IntegerSchema().format("int64")
                            .nullable(!Json.mapper().constructType(type.getType()).isPrimitive())
                            .description(schema.getDescription());
                }
            }
            return schema;
        };
    }

    @Bean
    public ModelConverter jsonFormatOpenApiConverter() {
        return (type, context, chain) -> {
            var schema = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
            if (schema == null) return null;
            if (type.getType() != null && "string".equals(schema.getType())
                    && Json.mapper().constructType(type.getType()).hasRawClass(LocalDateTime.class)) {
                schema = Json.mapper().convertValue(schema, StringSchema.class);
                schema.setFormat(null);
                schema.addExtension("x-date-format", "ISO_LOCAL_DATE_TIME");
                schema.setExample("2026-09-29T10:30:00");
            }
            if (type.getCtxAnnotations() == null) return schema;
            for (var annotation : type.getCtxAnnotations()) {
                if (annotation instanceof JsonFormat format && !format.pattern().isBlank()
                        && "string".equals(schema.getType())) {
                    // 非 RFC3339 的本地时间不能误标为 format: date-time。
                    // DateTimeSchema 会把非 RFC3339 示例转为 null，因此转换为普通字符串 Schema。
                    schema = Json.mapper().convertValue(schema, StringSchema.class);
                    schema.setFormat(null);
                    schema.addExtension("x-date-format", format.pattern());
                    if ("yyyy-MM-dd HH:mm:ss".equals(format.pattern())) schema.setExample("2026-09-29 10:30:00");
                    if ("yyyy-MM-dd".equals(format.pattern())) schema.setExample("2026-09-29");
                    if ("HH:mm".equals(format.pattern())) schema.setExample("10:30");
                }
            }
            return schema;
        };
    }
}
