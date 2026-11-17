package top.aiolife.sso.convertor;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import top.aiolife.sso.pojo.entity.ApiKeyEntity;
import top.aiolife.sso.pojo.vo.ApiKeyVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API Key 对象转换器
 *
 * @author Lys
 * @date 2026/03/09
 */
@Mapper(builder = @Builder(disableBuilder = true))
public interface ApiKeyConvertor {

    ApiKeyConvertor INSTANCE = Mappers.getMapper(ApiKeyConvertor.class);

    @Mapping(target = "apiKey", source = "keyPrefix", qualifiedByName = "maskApiKey")
    @Mapping(target = "isExpired", source = "expiredAt", qualifiedByName = "checkExpired")
    ApiKeyVO entity2VO(ApiKeyEntity entity);

    List<ApiKeyVO> entityList2VOList(List<ApiKeyEntity> entities);

    /**
     * 脱敏 API Key
     */
    @Named("maskApiKey")
    default String maskApiKey(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            return keyPrefix;
        }
        return keyPrefix + "***";
    }

    /**
     * 检查是否过期
     */
    @Named("checkExpired")
    default Boolean checkExpired(LocalDateTime expiredAt) {
        if (expiredAt == null) {
            return false;
        }
        return expiredAt.isBefore(LocalDateTime.now());
    }
}
