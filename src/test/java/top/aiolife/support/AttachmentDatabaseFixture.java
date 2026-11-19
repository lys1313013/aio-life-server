package top.aiolife.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import java.time.Duration;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import top.aiolife.record.mapper.*;
import top.aiolife.system.mapper.StorageObjectMapper;

/** 所有连接均指向测试自行创建的PostgreSQL，不读取业务配置。 */
public abstract class AttachmentDatabaseFixture {
    private static final GenericContainer<?> PG=new GenericContainer<>("postgres:18-alpine")
        .withEnv("POSTGRES_PASSWORD","attachment-fixture").withEnv("POSTGRES_DB","fixture").withExposedPorts(5432)
        .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n",2)).withStartupTimeout(Duration.ofMinutes(2));
    protected static JdbcTemplate jdbc;
    protected static SqlSessionTemplate session;
    protected static DataSourceTransactionManager manager;
    protected static TransactionTemplate transaction;
    @BeforeAll static synchronized void initialize() throws Exception {
        if(jdbc!=null) return;
        PG.start();
        var ds=new PGSimpleDataSource();
        ds.setURL("jdbc:postgresql://"+PG.getHost()+":"+PG.getMappedPort(5432)+"/fixture?stringtype=unspecified");
        ds.setUser("postgres"); ds.setPassword("attachment-fixture");
        jdbc=new JdbcTemplate(ds); manager=new DataSourceTransactionManager(ds); transaction=new TransactionTemplate(manager);
        CurrentSchema.initializePostgresFixture(jdbc);
        var configuration=new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        for(var mapper:java.util.List.of(IBVideoMapper.class,IAttachmentMapper.class,ExternalResourceMapper.class,ImageImportTaskMapper.class,
            StorageObjectMapper.class,top.aiolife.system.mapper.StorageFileReferenceMapper.class,PublicImageMapper.class,AttachmentCollectionMapper.class,
            ICbtiPersonalityMapper.class,ICbtiResultMapper.class)) configuration.addMapper(mapper);
        var factory=new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(configuration);
        session=new SqlSessionTemplate(factory.getObject());
    }
    @BeforeEach void resetAttachments() {
        jdbc.execute("TRUNCATE attachment,external_resource,import_job,storage_object,b_video,sys_user");
        session.clearCache();
    }
    protected <T> T mapper(Class<T> type) { return session.getMapper(type); }
    @SuppressWarnings("unchecked") protected <T> T proxy(T target) {
        var factory=new ProxyFactory(target); factory.setProxyTargetClass(true);
        var advice=new TransactionInterceptor(); advice.setTransactionManager(manager);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); factory.addAdvice(advice);
        return (T)factory.getProxy();
    }
}
