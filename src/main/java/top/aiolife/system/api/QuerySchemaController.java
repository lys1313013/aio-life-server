package top.aiolife.system.api;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.exception.NotRoleException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.system.pojo.req.SchemaRelationSaveReq;
import top.aiolife.system.pojo.vo.QuerySchemaVO;
import top.aiolife.system.pojo.vo.SchemaRelationVO;
import top.aiolife.system.service.QuerySchemaService;

@Tag(name = "数据库关系图谱")
@RestController
@RequestMapping("/query/schema")
@SaCheckRole("admin")
@RequiredArgsConstructor
public class QuerySchemaController {
    private final QuerySchemaService schemaService;

    @Operation(summary = "读取数据库表结构和逻辑关系")
    @GetMapping
    public ApiResponse<QuerySchemaVO> snapshot(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ApiResponse.success(schemaService.snapshot());
    }

    @Operation(summary = "创建逻辑关系")
    @PostMapping("/relations")
    public ApiResponse<SchemaRelationVO> create(@CurrentUserId String userId,
                                                @Valid @RequestBody SchemaRelationSaveReq request) {
        return ApiResponse.success(schemaService.create(request, IdCodec.decode(userId)));
    }

    @Operation(summary = "更新逻辑关系")
    @PutMapping("/relations/{id}")
    public ApiResponse<SchemaRelationVO> update(@CurrentUserId String userId, @PathVariable String id,
                                                @Valid @RequestBody SchemaRelationSaveReq request) {
        return ApiResponse.success(schemaService.update(IdCodec.decode(id), request, IdCodec.decode(userId)));
    }

    @Operation(summary = "删除逻辑关系")
    @DeleteMapping("/relations/{id}")
    public ApiResponse<Void> delete(@CurrentUserId String userId, @PathVariable String id) {
        schemaService.delete(IdCodec.decode(id), IdCodec.decode(userId));
        return ApiResponse.success();
    }

    @ExceptionHandler(NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> forbidden() {
        return ApiResponse.error(HttpStatus.FORBIDDEN.value(), "仅管理员可访问数据库关系图谱");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> configurationError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
                .body(ApiResponse.error(error.getStatusCode().value(), error.getReason()));
    }
}
