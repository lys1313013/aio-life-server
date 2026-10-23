package top.aiolife.record.pojo.vo;

/** GCJ02 数值坐标；精度信息只辅助用户确认，不自动认定为某个房间。 */
public record FootprintLocationVO(String placeName, String address, String city, double longitude,
                                  double latitude, String coordinateSystem, String providerUid,
                                  boolean precise, String level, Region region) {
    /** 上游行政区划信息；客户端只匹配本地目录中的有效编码。 */
    public record Region(String districtCode, String townCode, String townName) {}

    public FootprintLocationVO(String placeName, String address, String city, double longitude,
                               double latitude, String coordinateSystem, String providerUid,
                               boolean precise, String level) {
        this(placeName, address, city, longitude, latitude, coordinateSystem, providerUid, precise, level, null);
    }
}
