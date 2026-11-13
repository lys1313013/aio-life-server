package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;
import java.util.Map;
import top.aiolife.record.pojo.entity.MbtiResultEntity;
import top.aiolife.record.pojo.req.MbtiResultSaveReq;
import top.aiolife.record.pojo.vo.MbtiResultDetailVO;

/**
 * MBTI测试结果服务接口
 *
 * @author Lys
 * @date 2026-03-23
 */
public interface IMbtiResultService extends IService<MbtiResultEntity> {

    /**
     * 创建 MBTI 测试。
     *
     * @return 包含 testId 的 Map
     */
    Map<String, Object> createTest();

    /**
     * 查询 MBTI 测试结果。
     *
     * @param testId 测试ID
     * @return 测试结果
     */
    Map<String, Object> checkTest(String testId);

    /**
     * 保存测试结果。
     *
     * @param result 测试结果实体
     */
    void saveResult(MbtiResultEntity result);

    /**
     * 保存当前用户提交的测试结果。
     *
     * @param userId 用户ID
     * @param req 测试结果请求体
     */
    void saveResult(long userId, MbtiResultSaveReq req);

    /**
     * 获取用户历史记录。
     *
     * @param userId 用户ID
     * @return 历史记录列表
     */
    List<MbtiResultEntity> getUserHistory(Long userId);

    /**
     * 获取本人测试结果详情。
     *
     * @param userId 用户ID
     * @param id 记录ID
     * @return 详情
     */
    MbtiResultDetailVO getDetailForUser(long userId, long id);

    /**
     * 删除测试结果（仅本人可删）。
     *
     * @param id 记录ID
     * @param userId 用户ID
     * @return 是否删除成功
     */
    boolean deleteResult(Long id, Long userId);

    /**
     * 删除本人测试结果。
     *
     * @param userId 用户ID
     * @param id 记录ID
     */
    void deleteResultForUser(long userId, long id);
}
