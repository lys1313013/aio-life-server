package top.aiolife.membership.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import top.aiolife.membership.mapper.IMembershipMapper;
import top.aiolife.membership.pojo.entity.MembershipRecordEntity;
import top.aiolife.membership.service.IMembershipService;

/**
 * 会员 Service 实现类
 *
 * @author Lys
 * @date 2026/08/06
 */
@Service
public class MembershipServiceImpl extends ServiceImpl<IMembershipMapper, MembershipRecordEntity> implements IMembershipService {
}
