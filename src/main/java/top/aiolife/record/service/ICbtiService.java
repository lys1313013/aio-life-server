package top.aiolife.record.service;

import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;
import top.aiolife.record.pojo.req.CbtiPersonalitySaveReq;
import top.aiolife.record.pojo.vo.CbtiPersonalityAdminVO;

/**
 * CBTI 服务接口
 *
 * @author Ethan
 * @date 2026/04/18
 */
public interface ICbtiService {

    /**
     * 获取 CBTI 题库与维度定义。
     *
     * @return 题库与维度定义的集合数据
     */
    Map<String, Object> getQuestions();

    /**
     * 获取人格类型列表（展示结构）。
     *
     * @return 人格类型列表
     */
    List<Map<String, Object>> listPersonalityViews();

    /**
     * 根据人格代码获取人格详情（展示结构）。
     *
     * @param code 人格代码
     * @return 人格详情
     */
    Map<String, Object> getPersonalityView(String code);

    /**
     * 计算 CBTI 测试结果并保存为历史记录。
     *
     * @param userId 用户ID
     * @param answers 答题数据（题号 -> 选项值）
     * @param hiddenAnswers 彩蛋答题数据
     * @return 测试结果（包含人格、匹配度、维度结果、匹配排行等）
     */
    Map<String, Object> testAndSave(long userId, Map<Integer, Integer> answers, Map<String, Object> hiddenAnswers);

    /**
     * 获取用户 CBTI 测试历史（展示结构）。
     *
     * @param userId 用户ID
     * @return 历史列表
     */
    List<Map<String, Object>> getUserHistoryView(long userId);

    /**
     * 获取用户 CBTI 测试历史详情（展示结构）。
     *
     * @param id 历史记录ID
     * @param userId 用户ID
     * @return 历史详情
     */
    Map<String, Object> getHistoryDetailView(long id, long userId);

    /**
     * 删除用户 CBTI 测试历史（逻辑删除）。
     *
     * @param id 历史记录ID
     * @param userId 用户ID
     */
    void deleteHistoryForUser(long id, long userId);

    /**
     * 判断对象名是否为已绑定的人格图片。
     *
     * @param objectName 对象名
     * @return 是否已绑定
     */
    boolean isBoundImageObject(String objectName);

    /**
     * 管理端查询人格列表。
     *
     * @return 人格列表
     */
    List<CbtiPersonalityAdminVO> listPersonalitiesForAdmin();

    /**
     * 管理端新增人格。
     *
     * @param userId 操作用户ID
     * @param req 人格信息
     * @return 新增后的人格详情
     */
    CbtiPersonalityAdminVO createPersonality(long userId, CbtiPersonalitySaveReq req);

    /**
     * 管理端更新人格。
     *
     * @param userId 操作用户ID
     * @param id 人格记录ID
     * @param req 人格信息
     * @return 更新后的人格详情
     */
    CbtiPersonalityAdminVO updatePersonality(long userId, long id, CbtiPersonalitySaveReq req);

    /**
     * 管理端删除人格（逻辑删除）。
     *
     * @param userId 操作用户ID
     * @param id 人格记录ID
     */
    void deletePersonality(long userId, long id);

    /**
     * 管理端上传/替换人格图片。
     *
     * @param userId 操作用户ID
     * @param code 人格代码
     * @param file 图片文件
     * @return 图片对象名与预览地址
     */
    Map<String, Object> uploadPersonalityImage(long userId, String code, MultipartFile file);

    /**
     * 手动触发初始化人格图片。
     */
    void initImages();
}
