package top.aiolife.system.pojo.vo;

import java.util.List;

public record SchemaTableVO(String name, String comment, List<SchemaColumnVO> columns) {}
