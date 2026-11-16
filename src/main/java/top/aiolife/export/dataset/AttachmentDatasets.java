package top.aiolife.export.dataset;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import top.aiolife.export.mapper.ExportDatasetMapper;
import top.aiolife.export.mapper.SqlDatasetDefinition;
import top.aiolife.export.mapper.SqlDatasetDefinition.ParentScope;

import java.util.List;

/** 附件只读本人有效业务引用；对外导出 file_key，不暴露内部附件主键及存储路径。 */
@Component
@RequiredArgsConstructor
public class AttachmentDatasets implements ExportDatasetContributor {
    private final ExportDatasetMapper mapper;

    public List<ExportDataset> datasets() {
        return List.of(
                attachment("wardrobe_item_cover", "衣柜附件", "wardrobe_item", "/wardrobe"),
                attachment("feedback", "反馈附件", "feedback", "/my-hub/feedback"),
                attachment("movie_cover", "影视附件", "movie", "/record/movie"),
                attachment("honor_record", "荣誉附件", "honor_record", "/my-hub/honor"),
                attachment("read_cover", "阅读附件", "read_record", "/record/read"),
                attachment("event_record", "活动附件", "event_record", "/record/event-records"),
                attachment("device_cover", "设备附件", "device", "/my-hub/device"),
                attachment("bank_card_face", "银行卡附件", "bank_card", "/finance/bank-cards"),
                attachment("b_video_cover", "视频附件", "b_video", "/record/videoWatch"));
    }

    private ExportDataset attachment(String type, String title, String parent, String menu) {
        var columns =
                List.of(
                        new ExportColumn("id", "附件ID", "TEXT"),
                        new ExportColumn("file_size", "字节数", "NUMBER"),
                        new ExportColumn("file_type", "类型", "TEXT"),
                        new ExportColumn("biz_type", "业务类型", "TEXT"),
                        new ExportColumn("biz_id", "业务记录ID", "TEXT"),
                        new ExportColumn("create_time", "创建时间", "TEXT"));
        return new SqlExportDataset(
                new SqlDatasetDefinition(
                        "files_" + switch (type) {
                            case "wardrobe_item_cover" -> "wardrobe_item";
                            case "movie_cover" -> "movie";
                            case "read_cover" -> "read";
                            case "device_cover" -> "device";
                            case "bank_card_face" -> "bank_card_cover";
                            default -> type;
                        },
                        title,
                        "attachment",
                        "user_id",
                        type,
                        List.of(menu),
                        List.of(new ParentScope(parent, "biz_id", "id", "user_id")),
                        columns),
                mapper);
    }
}
