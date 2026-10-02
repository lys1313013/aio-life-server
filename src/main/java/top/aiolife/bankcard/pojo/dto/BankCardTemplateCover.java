package top.aiolife.bankcard.pojo.dto;

/** 当前用户已引用的公共卡面信息。 */
public record BankCardTemplateCover(Long id, String name, String sourceUrl, String fileId) {}
