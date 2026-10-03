package top.aiolife.record.pojo.vo;

/** 视频封面状态；ID 按全局 Long 序列化规则输出字符串。 */
public record BVideoCoverVO(Long id, String coverFileId, String coverState) {}
