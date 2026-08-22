package top.aiolife.record.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.record.mapper.IIncomeMapper;
import top.aiolife.record.pojo.entity.IncomeEntity;
import top.aiolife.record.pojo.query.IncomeQuery;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IncomeController 集成测试
 * 用于验证 SQL 脚本执行是否正确
 *
 * @author Lys
 * @date 2026/05/23
 */
class IncomeControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IncomeController incomeController;

    @Autowired
    private IIncomeMapper incomeMapper;

    @Test
    void testQuery_查询收入记录() {
        Long incomeId = System.currentTimeMillis() % 1000000 + 900000L;
        incomeMapper.insert(createIncome(incomeId));

        CommonQuery<IncomeQuery> query = new CommonQuery<>();
        query.setPage(1);
        query.setPageSize(10);
        query.setCondition(new IncomeQuery());

        var response = incomeController.query(query);
        assertSuccess(response);
        assertNotNull(response.getData());
        assertTrue(response.getData().getTotal() >= 1);
    }

    @Test
    void testStatisticsByYear_年度统计() {
        Long incomeId = System.currentTimeMillis() % 1000000 + 900000L;
        incomeMapper.insert(createIncome(incomeId));

        var response = incomeController.statisticsByYear();
        assertSuccess(response);
        assertNotNull(response.getData());
    }

    @Test
    void testStatisticsByMonth_月度统计() {
        Long incomeId = System.currentTimeMillis() % 1000000 + 900000L;
        incomeMapper.insert(createIncome(incomeId));

        var response = incomeController.statisticsByMonth();
        assertSuccess(response);
        assertNotNull(response.getData());
    }

    private IncomeEntity createIncome(Long incomeId) {
        IncomeEntity entity = new IncomeEntity();
        entity.setId(incomeId);
        entity.setUserId(TEST_USER_ID);
        entity.setIncTypeId(1L);
        entity.setAmt(BigDecimal.valueOf(100));
        entity.setIncDate(LocalDate.now());
        entity.setIsDeleted(0);
        return entity;
    }
}
