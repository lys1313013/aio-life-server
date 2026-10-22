package top.aiolife.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import top.aiolife.core.query.BoundedPaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * @author lys
 */
@Configuration
@MapperScan("top.aiolife.*.mapper")
public class MybatisPlusConfig {

  /**
   * 添加分页插件
   */
  @Bean
  public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    // 如果配置多个插件, 切记分页最后添加
    interceptor.addInnerInterceptor(new BoundedPaginationInnerInterceptor());
    // 如果有多数据源可以不配具体类型, 否则都建议配上具体的 DbType
    return interceptor;
  }

  /**
   * 显式声明 MySQL 事务管理器并标记为 @Primary。
   * 当 Neo4j 也在 classpath 时，spring-data-neo4j 会注册一个同名 transactionManager，
   * 借助 @ConditionalOnMissingBean 会让 Spring Boot 的 DataSourceTransactionManagerAutoConfiguration 跳过，
   * 导致所有 @Transactional 全部路由到 Neo4jTransactionManager，Neo4j 不可用时 MySQL 写入也会报错。
   * 这里显式 @Primary 强制走 JDBC 事务管理器。
   */
  @Bean(name = "dataSourceTransactionManager")
  @Primary
  public DataSourceTransactionManager dataSourceTransactionManager(DataSource dataSource) {
    return new DataSourceTransactionManager(dataSource);
  }
}