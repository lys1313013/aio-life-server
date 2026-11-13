package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.BVideoEntity;
import top.aiolife.record.pojo.query.BVideoQuery;
import top.aiolife.record.pojo.req.BVideoCreateReq;
import top.aiolife.record.pojo.req.BVideoProgressReq;
import top.aiolife.record.pojo.req.BVideoUpdateReq;
import top.aiolife.record.pojo.vo.BVideoCoverVO;
import top.aiolife.record.pojo.vo.BVideoStatisticsVO;
import top.aiolife.record.pojo.vo.BVideoVO;
import top.aiolife.record.service.IBVideoService;

/**
 * 哔哩哔哩学习视频控制器
 *
 * @author Lys
 * @date 2025/10/06 23:13
 */
@Tag(name = "哔哩哔哩学习视频")
@RestController
@AllArgsConstructor
@RequestMapping("/b-video")
public class BVideoController {

    private final IBVideoService bVideoService;

    @Operation(summary = "分页查询哔哩哔哩视频记录")
    @GetMapping("/query")
    public ApiResponse<PageResp<BVideoVO>> query(
            @CurrentUserId String userId,
            @QueryParams CommonQuery<BVideoQuery> requestQuery) {
        CommonQuery<BVideoEntity> query = requestQuery.map(RecordApiConvertor.INSTANCE::fromBVideoQuery);
        PageResp<BVideoEntity> page = bVideoService.pageForUser(
                IdCodec.decode(userId),
                query.getCondition(),
                query.getPage(),
                query.getPageSize());
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toBVideoVOPage(page));
    }

    /** 插件按 BV 号读取本人标记；未标记返回空，复用视频业务的二级锁。 */
    @Operation(summary = "按 BV 号查询我的视频标记")
    @GetMapping("/by-bvid")
    public ApiResponse<BVideoVO> byBvid(
            @CurrentUserId String userId,
            @RequestParam @Pattern(regexp = "BV[0-9A-Za-z]{10}") String bvid) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toBVideoVO(
                bVideoService.findByBvid(IdCodec.decode(userId), bvid)));
    }

    /** 按本人 ID 获取完整编辑详情，复用视频业务的二级锁。 */
    @Operation(summary = "查询哔哩哔哩视频详情")
    @GetMapping("/{id}")
    public ApiResponse<BVideoVO> detail(@CurrentUserId String userId, @PathVariable String id) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toBVideoVO(
                bVideoService.getOwned(IdCodec.decode(userId), IdCodec.decode(id))));
    }

    @Operation(summary = "新增哔哩哔哩视频记录")
    @PostMapping
    public ApiResponse<Boolean> insert(@CurrentUserId String userId,
                                       @Valid @RequestBody BVideoCreateReq entityReq) {
        BVideoEntity entity = RecordApiConvertor.INSTANCE.fromBVideoCreateReq(entityReq);
        bVideoService.createForUser(IdCodec.decode(userId), entity);
        return ApiResponse.success(true);
    }

    @Operation(summary = "更新哔哩哔哩视频记录")
    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(@CurrentUserId String userId, @PathVariable String id,
                                       @Valid @RequestBody BVideoUpdateReq entityReq) {
        BVideoEntity entity = RecordApiConvertor.INSTANCE.fromBVideoUpdateReq(entityReq);
        return ApiResponse.success(bVideoService.updateForUser(
                IdCodec.decode(userId), IdCodec.decode(id), entity));
    }

    @Operation(summary = "删除哔哩哔哩视频记录")
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@CurrentUserId String userId, @PathVariable String id) {
        return ApiResponse.success(bVideoService.deleteForUser(
                IdCodec.decode(userId), IdCodec.decode(id)));
    }

    /** 只读取导入状态，不发起外网下载；批量限制防止无限 IN 查询。 */
    @Operation(summary = "查询视频封面导入状态")
    @GetMapping("/covers")
    public ApiResponse<List<BVideoCoverVO>> covers(@CurrentUserId String userId,
                                                   @RequestParam List<String> ids) {
        return ApiResponse.success(bVideoService.listCovers(
                IdCodec.decode(userId), IdCodec.decodeAll(ids)));
    }

    @Operation(summary = "重试导入视频封面")
    @PostMapping("/{id}/cover/retry")
    public ApiResponse<Void> retryCover(@CurrentUserId String userId, @PathVariable String id) {
        bVideoService.retryCover(IdCodec.decode(userId), IdCodec.decode(id));
        return ApiResponse.success();
    }

    @Operation(summary = "统计视频观看状态数量")
    @GetMapping("/getStatusCount")
    public ApiResponse<Map<String, Integer>> getStatusCount(@CurrentUserId String userId) {
        return ApiResponse.success(bVideoService.statusCount(IdCodec.decode(userId)));
    }

    @Operation(summary = "查询视频观看统计")
    @GetMapping("/statistics")
    public ApiResponse<BVideoStatisticsVO> statistics(@CurrentUserId String userId) {
        return ApiResponse.success(bVideoService.statistics(IdCodec.decode(userId)));
    }

    @Operation(summary = "标记哔哩哔哩视频")
    @PostMapping("/tagVideo")
    public ApiResponse<Boolean> tagVideo(@CurrentUserId String userId,
                                         @Valid @RequestBody BVideoCreateReq entityReq) {
        BVideoEntity entity = RecordApiConvertor.INSTANCE.fromBVideoCreateReq(entityReq);
        bVideoService.tagForUser(IdCodec.decode(userId), entity);
        return ApiResponse.success();
    }

    @Operation(summary = "同步视频观看进度")
    @PostMapping("/syncProgress")
    public ApiResponse<Boolean> syncProgress(@CurrentUserId String userId,
                                             @Valid @RequestBody BVideoProgressReq entityReq) {
        BVideoEntity entity = RecordApiConvertor.INSTANCE.fromBVideoProgressReq(entityReq);
        bVideoService.syncProgress(IdCodec.decode(userId), entity);
        return ApiResponse.success();
    }
}
