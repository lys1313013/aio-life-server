package top.aiolife.membership.convertor;

import org.mapstruct.*;
import org.mapstruct.factory.Mappers;
import top.aiolife.membership.pojo.req.MembershipCreateReq;
import top.aiolife.membership.pojo.req.MembershipReq;

@Mapper(builder = @Builder(disableBuilder = true))
public interface MembershipApiConvertor {
    MembershipApiConvertor INSTANCE = Mappers.getMapper(MembershipApiConvertor.class);
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    MembershipReq fromMembershipCreateReq(MembershipCreateReq request);
}
