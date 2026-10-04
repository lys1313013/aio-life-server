package top.aiolife.system.service;

import top.aiolife.system.pojo.req.QuickNavSaveReq;
import top.aiolife.system.pojo.vo.QuickNavCandidateVO;
import top.aiolife.system.pojo.vo.QuickNavItemVO;

import java.util.List;

/**
 * 用户快捷导航服务接口
 *
 * @author Ethan
 * @date 2026/06/05
 */
public interface IQuickNavService {

    /**
     * 获取当前用户已保存的快捷导航布局。
     *
     * <p>未保存任何项时返回空列表（前端展示空态）。</p>
     *
     * @param userId 当前用户 ID
     * @return 已保存的布局，按 sortOrder 升序
     */
    List<QuickNavItemVO> listMy(long userId);

    List<QuickNavItemVO> listMy(long userId, List<String> roles, MenuClient client);

    /**
     * 获取当前用户可访问、且适合作为快捷入口的菜单叶子列表。
     *
     * @param roles 当前用户角色列表
     * @return 候选池
     */
    default List<QuickNavCandidateVO> listCandidates(List<String> roles) {
        return listCandidates(roles, MenuClient.WEB);
    }
    List<QuickNavCandidateVO> listCandidates(List<String> roles, MenuClient client);

    /**
     * 覆盖保存当前端可用的快捷导航布局，保留另一端独有配置。
     *
     * <p>items 可为空数组（= 清空当前端）；两端合计超过 12 项、菜单无权限、sortOrder 重复均拒绝。</p>
     *
     * @param userId 当前用户 ID
     * @param roles  当前用户角色列表（用于权限校验）
     * @param req    保存请求
     * @return 保存后的布局
     */
    List<QuickNavItemVO> saveMy(long userId, List<String> roles, QuickNavSaveReq req);
    List<QuickNavItemVO> saveMy(long userId, List<String> roles, QuickNavSaveReq req, MenuClient client);
}
