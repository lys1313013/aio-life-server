package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.pojo.entity.ExpenseEntity;
import top.aiolife.record.pojo.query.ExpenseQuery;
import top.aiolife.record.pojo.vo.ExpStaticByYearVO;

/**
 * 支出服务
 *
 * @author Lys
 * @date 2025/10/05 21:41
 */
public interface IExpenseService extends IService<ExpenseEntity> {

    /** 当前用户的支出分页，保留支出时间与 ID 的稳定倒序。 */
    PageResp<ExpenseEntity> pageForUser(long userId, ExpenseQuery condition, int page, int pageSize);

    /** 仅更新当前用户拥有的支出，审计时间由服务端维护。 */
    void updateForUser(long userId, ExpenseEntity entity);

    /** 按年度和支出分类统计，分类名称取用户可见字典。 */
    List<ExpStaticByYearVO> statisticsByYear(long userId);

    /** 按月份和支出分类统计，分类名称取用户可见字典。 */
    List<ExpStaticByYearVO> statisticsByMonth(long userId);
}
