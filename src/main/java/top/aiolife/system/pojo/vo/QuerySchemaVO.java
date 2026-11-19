package top.aiolife.system.pojo.vo;

import java.util.List;

public record QuerySchemaVO(String schema, List<SchemaTableVO> tables, List<SchemaRelationVO> relations) {}
