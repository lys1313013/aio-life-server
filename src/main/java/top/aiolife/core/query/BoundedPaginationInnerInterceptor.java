package top.aiolife.core.query;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.toolkit.ParameterUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.SQLException;

/** 禁止负数取消分页或静默截短超大页；覆盖未经过 HTTP DTO 的内部调用。 */
public class BoundedPaginationInnerInterceptor extends PaginationInnerInterceptor {
    public BoundedPaginationInnerInterceptor() {
        super(DbType.MYSQL);
        setMaxLimit((long) PaginationRequest.MAX_PAGE_SIZE);
    }

    private void validate(Object parameter) {
        ParameterUtils.findPage(parameter).ifPresent(page ->
                PaginationRequest.validate(page.getCurrent(), page.getSize(),
                        page.maxLimit() == null ? PaginationRequest.MAX_PAGE_SIZE
                                : Math.min(page.maxLimit(), PaginationRequest.MAX_PAGE_SIZE)));
    }

    @Override
    public boolean willDoQuery(Executor executor, MappedStatement statement, Object parameter,
                              RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        validate(parameter);
        return super.willDoQuery(executor, statement, parameter, rowBounds, resultHandler, boundSql);
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement statement, Object parameter,
                            RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        validate(parameter);
        super.beforeQuery(executor, statement, parameter, rowBounds, resultHandler, boundSql);
    }
}
