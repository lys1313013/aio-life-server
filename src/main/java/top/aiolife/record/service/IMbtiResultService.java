package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import top.aiolife.record.pojo.entity.MbtiResultEntity;

import java.util.List;
import java.util.Map;

/**
 * MBTI测试结果服务接口
 *
 * @author Lys
 * @date 2026-03-23
 */
public interface IMbtiResultService extends IService<MbtiResultEntity> {
    
    /**
     * 创建MBTI测试
     * @return 包含testId的Map
     */
    Map<String, Object> createTest();
    
    /**
     * 查询MBTI测试结果
     * @param testId 测试ID
     * @return 测试结果
     */
    Map<String, Object> checkTest(String testId);
    
    /**
     * 保存测试结果
     * @param result 测试结果实体
     */
    void saveResult(MbtiResultEntity result);
    
    /**
     * 获取用户历史记录
     * @param userId 用户ID
     * @return 历史记录列表
     */
    List<MbtiResultEntity> getUserHistory(Long userId);

    /**
     * 删除测试结果（仅本人可删）
     * @param id 记录ID
     * @param userId 用户ID
     * @return 是否删除成功
     */
    boolean deleteResult(Long id, Long userId);
}
