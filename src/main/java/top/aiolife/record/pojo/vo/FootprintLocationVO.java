package top.aiolife.record.pojo.vo;

/** GCJ02 数值坐标；精度信息只辅助用户确认，不自动认定为某个房间。 */
public record FootprintLocationVO(String placeName, String address, String city, double longitude,
                                  double latitude, String coordinateSystem, String providerUid,
                                  boolean precise, String level) {}
