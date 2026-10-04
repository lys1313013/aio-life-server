package top.aiolife.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import top.aiolife.record.pojo.vo.TimeRecordListVO;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.service.UserDictDataCache;

/**
 * Redis配置类
 *
 * @author Lys
 * @date 2025/12/06 23:58
 */
@Configuration
@EnableCaching
@Slf4j
public class RedisConfig implements CachingConfigurer {

    /**
     * 配置 CacheManager
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory, ObjectMapper objectMapper) {
        RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(name -> name + ":") // 替换默认的 "::" 分隔符为 ":"
                .entryTtl(Duration.ofHours(1)) // 默认缓存1小时
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        var daySerializer = new Jackson2JsonRedisSerializer<List<TimeRecordListVO>>(objectMapper,
                objectMapper.getTypeFactory().constructCollectionType(List.class, TimeRecordListVO.class));
        var dayConfiguration = cacheConfiguration.entryTtl(Duration.ofHours(1))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(daySerializer));
        var dictSerializer = new Jackson2JsonRedisSerializer<List<UserDictDataEntity>>(objectMapper,
                objectMapper.getTypeFactory().constructCollectionType(List.class, UserDictDataEntity.class));
        var dictConfiguration = cacheConfiguration.entryTtl(Duration.ofMinutes(5))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(dictSerializer));
        return RedisCacheManager.builder(factory)
                .cacheDefaults(cacheConfiguration)
                .withInitialCacheConfigurations(Map.of("timeRecordDay:v1", dayConfiguration,
                        UserDictDataCache.CACHE_NAME, dictConfiguration))
                .build();
    }

    /** 日列表和字典缓存故障时回源；其他缓存保留原有异常行为。 */
    @Override
    @Bean
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            private void handle(RuntimeException exception, Cache cache) {
                if (!"timeRecordDay:v1".equals(cache.getName())
                        && !UserDictDataCache.CACHE_NAME.equals(cache.getName())) throw exception;
                log.warn("Cache operation failed: cache={}", cache.getName(), exception);
            }
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) { handle(e, cache); }
            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) { handle(e, cache); }
            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) { handle(e, cache); }
            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) { handle(e, cache); }
        };
    }
}
