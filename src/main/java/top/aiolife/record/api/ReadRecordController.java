package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

import java.util.List;
import jakarta.validation.Valid;
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

@Tag(name = "阅读 书籍 文章")
@RestController
@RequestMapping("/read-record")
@RequiredArgsConstructor
@SaCheckLogin
public class ReadRecordController {

    private final IReadRecordService readRecordService;

    @GetMapping("/page")
    @Operation(summary = "分页查询阅读记录")
    public ApiResponse<PageResp<ReadRecordVO>> pageList(@QueryParams ReadRecordQuery query) {
        var page = readRecordService.pageList(query);
        return ApiResponse.success(PageResp.of(page.getRecords(), page.getTotal()));
    }

    @PostMapping
    @Operation(summary = "新增阅读记录并返回字符串 ID；相同豆瓣条目返回已有 ID")
    public ApiResponse<String> save(@Valid @RequestBody ReadRecordCreateReq request) {
        ReadRecordReq req = RecordApiConvertor.INSTANCE.fromReadRecordCreateReq(request);
        return ApiResponse.success(String.valueOf(readRecordService.saveRecord(req)));
    }

    @PutMapping
    @Operation(summary = "更新阅读记录")
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
    @Operation(summary = "读取阅读记录详情")
    public ApiResponse<ReadRecordVO> getById(@PathVariable Long id) {
        return ApiResponse.success(readRecordService.getVOById(id));
    }

    @GetMapping("/parse-douban")
    @Operation(summary = "解析豆瓣书籍信息")
    public ApiResponse<ReadRecordReq> parseDouban(@RequestParam String url) {
        return ApiResponse.success(readRecordService.parseDouban(url));
    }

    @GetMapping("/active")
    @Operation(summary = "查询想读及在读记录")
    public ApiResponse<List<ReadRecordVO>> listActive() {
        return ApiResponse.success(readRecordService.listActive());
    }

}
