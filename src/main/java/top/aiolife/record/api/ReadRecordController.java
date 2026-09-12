package top.aiolife.record.api;

import top.aiolife.core.query.QueryParams;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.pojo.query.ReadRecordQuery;
import top.aiolife.record.pojo.req.ReadRecordReq;
import top.aiolife.record.pojo.vo.ReadRecordVO;
import top.aiolife.record.service.IReadRecordService;

@RestController
@RequestMapping("/read-record")
@RequiredArgsConstructor
@SaCheckLogin
public class ReadRecordController {

    private final IReadRecordService readRecordService;

    @GetMapping("/page")
    public ApiResponse<Page<ReadRecordVO>> pageList(@QueryParams ReadRecordQuery query) {
        return ApiResponse.success(readRecordService.pageList(query));
    }

    @PostMapping
    public ApiResponse<Void> save(@RequestBody ReadRecordReq req) {
        readRecordService.saveRecord(req);
        return ApiResponse.success();
    }

    @PutMapping
    public ApiResponse<Void> update(@RequestBody ReadRecordReq req) {
        readRecordService.updateRecord(req);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        readRecordService.deleteRecord(id);
        return ApiResponse.success();
    }

    @GetMapping("/{id}")
    public ApiResponse<ReadRecordVO> getById(@PathVariable Long id) {
        return ApiResponse.success(readRecordService.getVOById(id));
    }

    @GetMapping("/parse-douban")
    public ApiResponse<ReadRecordReq> parseDouban(@RequestParam String url) {
        return ApiResponse.success(readRecordService.parseDouban(url));
    }

    @GetMapping("/active")
    public ApiResponse<java.util.List<ReadRecordVO>> listActive() {
        return ApiResponse.success(readRecordService.listActive());
    }

}
