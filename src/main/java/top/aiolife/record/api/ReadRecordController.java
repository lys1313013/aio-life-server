package top.aiolife.record.api;

import java.util.List;
import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.query.ReadRecordQuery;
import top.aiolife.record.pojo.req.ReadRecordCreateReq;
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
    public ApiResponse<PageResp<ReadRecordVO>> pageList(@QueryParams ReadRecordQuery query) {
        var page = readRecordService.pageList(query);
        return ApiResponse.success(PageResp.of(page.getRecords(), page.getTotal()));
    }

    @PostMapping
    public ApiResponse<Void> save(@RequestBody ReadRecordCreateReq request) {
        ReadRecordReq req = RecordApiConvertor.INSTANCE.fromReadRecordCreateReq(request);
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
    public ApiResponse<List<ReadRecordVO>> listActive() {
        return ApiResponse.success(readRecordService.listActive());
    }

}
