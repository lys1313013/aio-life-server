package top.aiolife.bankcard.pojo.vo;

import org.apache.ibatis.annotations.AutomapConstructor;

/** 管理端卡面信息，使用数量不暴露使用者信息。 */
public record BankCardCoverTemplateVO(String id, String name, String bankId, String bankName,
        String cardType, String sourceUrl, int isEnabled, int sortOrder, String fileId, long usageCount,
        String publicUrl) {
    @AutomapConstructor
    public BankCardCoverTemplateVO(String id, String name, String bankId, String bankName,
            String cardType, String sourceUrl, int isEnabled, int sortOrder, String fileId, long usageCount) {
        this(id, name, bankId, bankName, cardType, sourceUrl, isEnabled, sortOrder, fileId, usageCount, null);
    }
    public BankCardCoverTemplateVO withPublicUrl(String url) {
        return new BankCardCoverTemplateVO(id, name, bankId, bankName, cardType, sourceUrl,
                isEnabled, sortOrder, fileId, usageCount, url);
    }
    public record Option(String id, String name, String fileId, String publicUrl) {
        @AutomapConstructor
        public Option(String id, String name, String fileId) { this(id, name, fileId, null); }
    }
}
