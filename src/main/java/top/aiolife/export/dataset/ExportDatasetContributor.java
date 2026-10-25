package top.aiolife.export.dataset;

import java.util.List;

/** 一个业务模块可注册多个互相关联的数据集。 */
public interface ExportDatasetContributor {
    List<ExportDataset> datasets();
}
