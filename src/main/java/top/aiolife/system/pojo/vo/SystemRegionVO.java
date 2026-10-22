package top.aiolife.system.pojo.vo;

/** 层级为1省、2市、3区县、4乡镇街道。 */
public record SystemRegionVO(String code, String name, Integer level, boolean hasChildren) {}
