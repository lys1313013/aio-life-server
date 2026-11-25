package top.aiolife.record.service;

import top.aiolife.record.pojo.csdn.CsdnArticleVO;
import top.aiolife.record.pojo.csdn.CsdnStatsVO;

import java.util.List;

/**
 * CSDN 数据服务接口
 */
public interface ICsdnService {

    /** 查询公开主页，返回可用于确认账号的名称。 */
    String verifyAccount(String username);
    
    /**
     * 获取 CSDN 用户数据统计
     * @param username CSDN 用户名
     * @return 统计数据
     */
    CsdnStatsVO getCsdnStats(String username);

    /**
     * 获取 CSDN 用户最近文章列表
     * @param username CSDN 用户名
     * @param limit 获取条数
     * @return 文章列表
     */
    List<CsdnArticleVO> getCsdnArticles(String username, Integer limit);
}
