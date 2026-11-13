package top.aiolife.membership.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.membership.pojo.vo.MembershipProviderVO;
import top.aiolife.membership.service.MembershipIconCatalog;
import top.aiolife.membership.service.MembershipProviderService;

/**
 * 会员平台目录控制器。
 *
 * @author Lys
 * @date 2026/11/13
 */
@Tag(name = "会员平台")
@RestController
@RequiredArgsConstructor
@RequestMapping("/membership")
public class MembershipProviderController {
    private final MembershipProviderService service;
    private final MembershipIconCatalog icons;

    @Operation(summary = "查询可选会员平台")
    @GetMapping("/providers")
    public ApiResponse<List<MembershipProviderVO>> providers() {
        return ApiResponse.success(service.list(true));
    }

    @Operation(summary = "查询会员平台可选图标")
    @GetMapping("/provider-icons")
    public ApiResponse<List<MembershipIconCatalog.IconVO>> icons() {
        return ApiResponse.success(icons.list());
    }

    @Operation(summary = "读取会员平台图标")
    @GetMapping("/provider-icons/{key}")
    public ResponseEntity<Resource> icon(@PathVariable String key,
                                         @RequestParam(defaultValue = "false") boolean dark) {
        var icon = icons.find(key, dark).orElse(null);
        if (icon == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(CacheControl.noStore()).build();
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(icon.contentType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(icon.resource());
    }
}
