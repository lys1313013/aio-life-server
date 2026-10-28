package top.aiolife.wardrobe.pojo.dto;

/** 按分类 ID 汇总，名称为空表示分类不存在、已删除或当前用户不可见。 */
public record WardrobeCategoryCount(String categoryName, long itemCount) {
}
