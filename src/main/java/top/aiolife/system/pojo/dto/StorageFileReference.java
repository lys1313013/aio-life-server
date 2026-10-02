package top.aiolife.system.pojo.dto;

/** 对象存储删除保护使用的原始文件关联，包括软删除记录。 */
public record StorageFileReference(String id, String name, String owner, String bizType, String bizId, int deleted) {}
