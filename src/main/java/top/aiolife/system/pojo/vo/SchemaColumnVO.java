package top.aiolife.system.pojo.vo;

public record SchemaColumnVO(String name, String dataType, String comment, boolean nullable, boolean primaryKey) {}
