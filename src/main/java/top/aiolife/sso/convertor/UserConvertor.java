package top.aiolife.sso.convertor;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.pojo.vo.UserVO;

/**
 * 用户对象转换器
 *
 * @author Lys
 * @date 2026/03/09
 */
@Mapper(builder = @Builder(disableBuilder = true), unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserConvertor {

    UserConvertor INSTANCE = Mappers.getMapper(UserConvertor.class);

    // 在线状态由 UserServiceImpl 从 Sa-Token 会话查询，数据库实体不保存该状态。
    @Mapping(target = "isOnline", ignore = true)
    UserVO entity2VO(UserEntity userEntity);
}
