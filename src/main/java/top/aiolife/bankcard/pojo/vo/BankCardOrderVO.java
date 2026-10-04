package top.aiolife.bankcard.pojo.vo;

/** 排序补丁，不携带卡号或其他业务数据。 */
public record BankCardOrderVO(Long id, Integer sortOrder) {}
