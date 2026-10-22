package top.aiolife.record.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.record.convertor.LifeFootprintConvertor;
import top.aiolife.system.service.SystemRegionService;
import top.aiolife.record.mapper.LifeFootprintMapper;
import top.aiolife.record.pojo.entity.LifeFootprintEntity;
import top.aiolife.record.pojo.req.LifeFootprintSaveReq;
import top.aiolife.record.pojo.vo.LifeFootprintVO;

@Service
@RequiredArgsConstructor
public class LifeFootprintService {
    private final LifeFootprintMapper mapper;
    private final SystemRegionService regions;

    /** 个人长期地点全量读取，地图不依赖列表页码。 */
    public List<LifeFootprintVO> list(long userId) {
        return mapper.selectList(new LambdaQueryWrapper<LifeFootprintEntity>()
                .eq(LifeFootprintEntity::getUserId, userId)
                .orderByAsc(LifeFootprintEntity::getStartDate, LifeFootprintEntity::getId)).stream()
                .map(LifeFootprintConvertor.INSTANCE::toVO).toList();
    }

    public LifeFootprintVO get(long userId, long id) {
        return LifeFootprintConvertor.INSTANCE.toVO(require(userId, id));
    }

    @Transactional(rollbackFor = Exception.class)
    public LifeFootprintVO create(long userId, LifeFootprintSaveReq request) {
        var entity = prepare(request);
        entity.setUserId(userId);
        entity.fillCreateCommonField(userId);
        mapper.insert(entity);
        return LifeFootprintConvertor.INSTANCE.toVO(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public LifeFootprintVO update(long userId, long id, LifeFootprintSaveReq request) {
        var entity = prepare(request);
        entity.setId(id);
        entity.setUserId(userId);
        entity.fillUpdateCommonField(userId);
        if (mapper.update(entity, new LambdaUpdateWrapper<LifeFootprintEntity>()
                .eq(LifeFootprintEntity::getId, id).eq(LifeFootprintEntity::getUserId, userId)) != 1) throw notFound();
        return LifeFootprintConvertor.INSTANCE.toVO(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(long userId, long id) {
        // 用户归属和逻辑删除条件保留在同一条 SQL 中。
        if (mapper.delete(new LambdaQueryWrapper<LifeFootprintEntity>().eq(LifeFootprintEntity::getId, id)
                .eq(LifeFootprintEntity::getUserId, userId)) != 1) throw notFound();
    }

    private LifeFootprintEntity prepare(LifeFootprintSaveReq request) {
        LifeFootprintDates.validate(request.getStartDate(), request.getEndDate(),
                LocalDate.now(ZoneId.of("Asia/Shanghai")));
        regions.validateSelection(request.getRegionCode());
        var entity = LifeFootprintConvertor.INSTANCE.toEntity(request);
        entity.setPlaceName(request.getPlaceName().trim());
        entity.setAddress(trim(request.getAddress()));
        entity.setCity(trim(request.getCity()));
        entity.setNote(trim(request.getNote()));
        entity.setProviderUid(trim(request.getProviderUid()));
        entity.setLongitude(request.getLongitude().setScale(6, RoundingMode.HALF_UP));
        entity.setLatitude(request.getLatitude().setScale(6, RoundingMode.HALF_UP));
        return entity;
    }

    private LifeFootprintEntity require(long userId, long id) {
        var entity = mapper.selectOne(new LambdaQueryWrapper<LifeFootprintEntity>()
                .eq(LifeFootprintEntity::getId, id).eq(LifeFootprintEntity::getUserId, userId));
        if (entity == null) throw notFound();
        return entity;
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在或已删除");
    }
}
