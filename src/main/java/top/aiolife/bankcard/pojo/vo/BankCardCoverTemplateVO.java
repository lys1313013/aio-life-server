package top.aiolife.bankcard.pojo.vo;

/** 管理端卡面信息，使用数量不暴露使用者信息。 */
public record BankCardCoverTemplateVO(String id, String name, String bankId, String bankName,
        String cardType, String sourceUrl, int isEnabled, int sortOrder, String fileId, long usageCount) {
    public record Option(String id, String name, String fileId) {}
}
