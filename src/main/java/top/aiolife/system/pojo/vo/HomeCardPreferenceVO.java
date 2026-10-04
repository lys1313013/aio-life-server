package top.aiolife.system.pojo.vo;


public record HomeCardPreferenceVO(String cardKey, String group, String title, String icon,
                                   boolean enabled, int sortOrder) {}
