package top.aiolife.docs.api;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.docs.pojo.vo.DocumentationVO;
import top.aiolife.docs.service.DocumentationService;

/** 匿名文档发现接口；不加入业务文档，避免文档描述自身。 */
@Hidden
@RestController
@RequestMapping("/docs")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", matchIfMissing = true)
public class DocumentationController {
    private final DocumentationService documentationService;

    @GetMapping("/catalog")
    public ApiResponse<DocumentationVO.Catalog> catalog(HttpServletRequest request) {
        return ApiResponse.success(documentationService.catalog(request));
    }

    @GetMapping("/operations")
    public ApiResponse<DocumentationVO.OperationPage> operations(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String module,
            @RequestParam(defaultValue = "1") String page,
            @RequestParam(defaultValue = "20") String pageSize,
            HttpServletRequest request) {
        return ApiResponse.success(documentationService.search(request, keyword, module, page, pageSize));
    }

    @GetMapping("/operations/{operationId}")
    public ApiResponse<DocumentationVO.OperationDetail> operation(
            @PathVariable String operationId, HttpServletRequest request) {
        return ApiResponse.success(documentationService.detail(request, operationId));
    }
}
