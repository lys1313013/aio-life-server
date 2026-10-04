package top.aiolife.membership.api;

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
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.membership.pojo.vo.MembershipProviderVO;
import top.aiolife.membership.service.MembershipIconCatalog;
import top.aiolife.membership.service.MembershipProviderService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/membership")
public class MembershipProviderController {
    private final MembershipProviderService service;
    private final MembershipIconCatalog icons;

    @GetMapping("/providers")
    public ApiResponse<List<MembershipProviderVO>> providers() { return ApiResponse.success(service.list(true)); }

    @GetMapping("/provider-icons")
    public ApiResponse<List<MembershipIconCatalog.IconVO>> icons() { return ApiResponse.success(icons.list()); }

    @GetMapping("/provider-icons/{key}")
    public ResponseEntity<Resource> icon(@PathVariable String key) {
        String file;
        try {
            file = icons.require(key).file();
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(CacheControl.noStore()).build();
        }
        String type = file.endsWith(".svg") ? "image/svg+xml" : file.endsWith(".webp") ? "image/webp"
                : file.endsWith(".png") ? "image/png" : file.endsWith(".ico") ? "image/x-icon" : "image/jpeg";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(icons.resource(key));
    }
}
