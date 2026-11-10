package top.aiolife.config;

import org.neo4j.driver.Driver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

/**
 * Neo4j 配置
 *
 * 通过 aio.life.neo4j.enabled 控制（默认 false）。
 * 设置 AIO_LIFE_NEO4J_ENABLED=true 后加载；缺省或为 false 时关系图谱模块整体不加载，其他接口（timeRecord、wardrobe 等）可正常使用。
 *
 * 事务管理器说明：
 * - MybatisPlusConfig 中显式声明了 dataSourceTransactionManager 并标记 @Primary（PostgreSQL @Transactional 默认走它）。
 * - 由于 PlatformTransactionManager 已存在，Spring Data Neo4j 的自动配置会跳过 transactionManager 创建。
 * - 因此这里显式提供 neo4jTransactionManager，并通过 transactionManagerRef 指给 Neo4j Repository 使用。
 */
@Configuration
@ConditionalOnProperty(name = "aio.life.neo4j.enabled", havingValue = "true", matchIfMissing = false)
@EnableNeo4jRepositories(basePackages = "top.aiolife.relationship.repository", transactionManagerRef = "neo4jTransactionManager")
public class Neo4jConfig {

    @Bean(name = "neo4jTransactionManager")
    public Neo4jTransactionManager neo4jTransactionManager(Driver driver) {
        return new Neo4jTransactionManager(driver);
    }
}
