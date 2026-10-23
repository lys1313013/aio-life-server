package top.aiolife.record.client;

import java.util.List;
import top.aiolife.record.pojo.vo.FootprintLocationVO;

/** 地图查询适配接口；坐标统一 GCJ02，不暴露供应商凭据。 */
public interface FootprintMapProvider {
    boolean available();
    List<FootprintLocationVO> search(String keyword, String region);
    FootprintLocationVO geocode(String address);
    FootprintLocationVO reverse(double longitude, double latitude);
}
