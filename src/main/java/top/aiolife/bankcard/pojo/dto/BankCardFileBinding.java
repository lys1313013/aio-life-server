package top.aiolife.bankcard.pojo.dto;

/** 文件归属校验和卡面聚合需要的最小字段，不作为接口响应。 */
public record BankCardFileBinding(String id, Long bizId, Long createUser) {}
