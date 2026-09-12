package top.aiolife.record.api;

import top.aiolife.core.query.QueryParams;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.pojo.query.MovieQuery;
import top.aiolife.record.pojo.req.MovieReq;
import top.aiolife.record.pojo.req.DoubanMovieImportReq;
import top.aiolife.record.pojo.vo.DoubanMovieImportPreviewVO;
import top.aiolife.record.pojo.vo.DoubanMovieImportResultVO;
import top.aiolife.record.pojo.vo.MovieVO;
import top.aiolife.record.service.IDoubanMovieImportService;
import top.aiolife.record.service.IMovieService;

/**
 * 影视记录控制器
 *
 * @author Trae
 * @date 2026/06/18
 */
@RestController
@RequestMapping("/movie")
@RequiredArgsConstructor
@SaCheckLogin
public class MovieController {

    private final IMovieService movieService;

    private final IDoubanMovieImportService doubanMovieImportService;

    @GetMapping("/page")
    public ApiResponse<Page<MovieVO>> pageList(@QueryParams MovieQuery query) {
        return ApiResponse.success(movieService.pageList(query));
    }

    @PostMapping
    public ApiResponse<Void> save(@RequestBody MovieReq req) {
        movieService.saveRecord(req);
        return ApiResponse.success();
    }

    @PutMapping
    public ApiResponse<Void> update(@RequestBody MovieReq req) {
        movieService.updateRecord(req);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        movieService.deleteRecord(id);
        return ApiResponse.success();
    }

    @GetMapping("/{id}")
    public ApiResponse<MovieVO> getById(@PathVariable Long id) {
        return ApiResponse.success(movieService.getVOById(id));
    }

    @GetMapping("/parse-douban")
    public ApiResponse<MovieReq> parseDouban(@RequestParam String url) {
        return ApiResponse.success(movieService.parseDouban(url));
    }

    @PostMapping("/import/douban/preview")
    public ApiResponse<DoubanMovieImportPreviewVO> previewDoubanImport(
            @RequestBody DoubanMovieImportReq request) {
        return ApiResponse.success(doubanMovieImportService.preview(request));
    }

    @PostMapping("/import/douban")
    public ApiResponse<DoubanMovieImportResultVO> importDouban(
            @RequestBody DoubanMovieImportReq request) {
        return ApiResponse.success(doubanMovieImportService.importRecords(request));
    }

    @GetMapping("/active")
    public ApiResponse<java.util.List<MovieVO>> listActive() {
        return ApiResponse.success(movieService.listActive());
    }

}
