package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.pojo.entity.IncomeEntity;
import top.aiolife.record.pojo.query.IncomeQuery;
import top.aiolife.record.pojo.vo.IncStaticByYearVO;

/**
 * 收入服务
 *
 * @author Lys
 * @date 2025/09/14 21:09
 */
public interface IIncomeService extends IService<IncomeEntity> {

    /** 当前用户的收入分页，保留收入日期与 ID 的稳定倒序。 */
    PageResp<IncomeEntity> pageForUser(long userId, IncomeQuery condition, int page, int pageSize);

    /** 新增收入，用户归属和审计字段由服务端设置。 */
    boolean createForUser(long userId, IncomeEntity entity);

    /** 仅更新当前用户拥有的收入，不允许修改归属。 */
    boolean updateForUser(long userId, long incomeId, IncomeEntity entity);

    /** 仅逻辑删除当前用户拥有的收入。 */
    boolean deleteForUser(long userId, long incomeId);

    /** 按年度和收入分类统计，分类名称取用户可见字典。 */
    List<IncStaticByYearVO> statisticsByYear(long userId);

    /** 按月份和收入分类统计，分类名称取用户可见字典。 */
    List<IncStaticByYearVO> statisticsByMonth(long userId);
}
