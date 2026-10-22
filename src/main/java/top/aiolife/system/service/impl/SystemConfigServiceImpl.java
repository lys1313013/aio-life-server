package top.aiolife.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.aiolife.system.mapper.ISystemConfigMapper;
import top.aiolife.system.pojo.entity.SystemConfigEntity;
import top.aiolife.system.pojo.req.SystemConfigUpdateReq;
import top.aiolife.system.pojo.vo.SystemConfigVO;
import top.aiolife.system.service.ISystemConfigService;
import top.aiolife.system.service.WechatSystemConfigService;
import top.aiolife.system.service.FootprintMapConfigService;
import top.aiolife.system.service.FootprintTencentConfigService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统配置服务实现
 *
 * @author Ethan
 * @date 2026/07/19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements ISystemConfigService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ISystemConfigMapper systemConfigMapper;
    private final WechatSystemConfigService wechatConfig;
    private final FootprintMapConfigService footprintMapConfig;
    private final FootprintTencentConfigService footprintTencentConfig;

    @Override
    public List<SystemConfigVO> list(String keyPrefix) {
        LambdaQueryWrapper<SystemConfigEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyPrefix)) {
            wrapper.likeRight(SystemConfigEntity::getConfigKey, keyPrefix);
        }
        wrapper.orderByAsc(SystemConfigEntity::getConfigKey);
        List<SystemConfigEntity> entities = systemConfigMapper.selectList(wrapper);
        List<SystemConfigVO> result = entities.stream()
                .filter(e -> !WechatSystemConfigService.KEY.equals(e.getConfigKey()))
                .filter(e -> !FootprintMapConfigService.KEY.equals(e.getConfigKey()))
                .filter(e -> !FootprintTencentConfigService.KEY.equals(e.getConfigKey()))
                .map(this::toVO).collect(Collectors.toList());
        if (!StringUtils.hasText(keyPrefix) || WechatSystemConfigService.KEY.startsWith(keyPrefix)) {
            result.add(wechatConfig.getAdminConfig());
        }
        if (!StringUtils.hasText(keyPrefix) || FootprintMapConfigService.KEY.startsWith(keyPrefix)) {
            result.add(footprintMapConfig.getAdminConfig());
        }
        if (!StringUtils.hasText(keyPrefix) || FootprintTencentConfigService.KEY.startsWith(keyPrefix)) {
            result.add(footprintTencentConfig.getAdminConfig());
        }
        return result;
    }

    @Override
    public SystemConfigVO getByKey(String key) {
        if (FootprintTencentConfigService.KEY.equals(key)) return footprintTencentConfig.getAdminConfig();
        if (FootprintMapConfigService.KEY.equals(key)) return footprintMapConfig.getAdminConfig();
        if (WechatSystemConfigService.KEY.equals(key)) return wechatConfig.getAdminConfig();
        SystemConfigEntity entity = loadByKey(key);
        return entity == null ? null : toVO(entity);
    }

    @Override
    public String getValueByKey(String key) {
        if (FootprintTencentConfigService.KEY.equals(key)) return footprintTencentConfig.getAdminConfig().getConfigValue();
        if (FootprintMapConfigService.KEY.equals(key)) return footprintMapConfig.getAdminConfig().getConfigValue();
        SystemConfigEntity entity = loadByKey(key);
        return entity == null ? null : entity.getConfigValue();
    }

    @Override
    public SystemConfigVO update(String key, SystemConfigUpdateReq req, long operatorId) {
        if (FootprintTencentConfigService.KEY.equals(key)) return footprintTencentConfig.save(req.getConfigValue(), operatorId);
        if (FootprintMapConfigService.KEY.equals(key)) return footprintMapConfig.save(req.getConfigValue(), operatorId);
        if (WechatSystemConfigService.KEY.equals(key)) return wechatConfig.save(req.getConfigValue(), operatorId);
        SystemConfigEntity entity = loadByKey(key);
        if (entity == null) {
            throw new IllegalArgumentException("配置项不存在：" + key);
        }

        LambdaUpdateWrapper<SystemConfigEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SystemConfigEntity::getId, entity.getId())
                .set(SystemConfigEntity::getConfigValue, req.getConfigValue())
                .set(SystemConfigEntity::getUpdateUser, operatorId)
                .set(SystemConfigEntity::getUpdateTime, LocalDateTime.now());
        systemConfigMapper.update(null, updateWrapper);

        entity.setConfigValue(req.getConfigValue());
        entity.setUpdateUser(operatorId);
        entity.setUpdateTime(LocalDateTime.now());
        return toVO(entity);
    }

    private SystemConfigEntity loadByKey(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        LambdaQueryWrapper<SystemConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfigEntity::getConfigKey, key);
        return systemConfigMapper.selectOne(wrapper);
    }

    private SystemConfigVO toVO(SystemConfigEntity entity) {
        SystemConfigVO vo = new SystemConfigVO();
        vo.setConfigKey(entity.getConfigKey());
        vo.setConfigValue(entity.getConfigValue());
        vo.setConfigType(entity.getConfigType());
        vo.setDescription(entity.getDescription());
        vo.setUpdateTime(entity.getUpdateTime() == null ? null : entity.getUpdateTime().format(FORMATTER));
        return vo;
    }
}
