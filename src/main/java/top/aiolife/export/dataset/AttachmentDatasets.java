package top.aiolife.export.dataset;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import top.aiolife.export.mapper.ExportDatasetMapper;
import top.aiolife.export.mapper.SqlDatasetDefinition;
import top.aiolife.export.mapper.SqlDatasetDefinition.ParentScope;

import java.util.List;

/** 附件只读本人有效业务引用；不导出 file_name（它是内部对象路径）。 */
@Component
@RequiredArgsConstructor
public class AttachmentDatasets implements ExportDatasetContributor {
    private final ExportDatasetMapper mapper;

    public List<ExportDataset> datasets() {
        return List.of(
                attachment("wardrobe_item", "衣柜附件", "wardrobe_item", "/wardrobe"),
                attachment("feedback", "反馈附件", "feedback", "/my-hub/feedback"),
                attachment("movie", "影视附件", "movie", "/record/movie"),
                attachment("honor_record", "荣誉附件", "honor_record", "/my-hub/honor"),
                attachment("read", "阅读附件", "read_record", "/record/read"),
                attachment("performance", "演出附件", "performance", "/record/performance"),
                attachment("device", "设备附件", "device", "/my-hub/device"),
                attachment("bank_card_cover", "银行卡附件", "bank_card", "/finance/bank-cards"),
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
                        "files_" + type,
                        title,
                        "file",
                        "create_user",
                        type,
                        List.of(menu),
                        List.of(new ParentScope(parent, "biz_id", "id", "user_id")),
                        columns),
                mapper);
    }
}
