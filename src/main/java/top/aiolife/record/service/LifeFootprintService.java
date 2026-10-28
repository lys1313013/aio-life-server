package top.aiolife.record.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
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
import top.aiolife.record.pojo.req.LifeFootprintBatchSaveReq;
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

    /** 整个编辑会话原子保存；旧的单条接口继续服务其他客户端。 */
    @Transactional(rollbackFor = Exception.class)
    public List<LifeFootprintVO> saveBatch(long userId, LifeFootprintBatchSaveReq request) {
        var items = request.getItems();
        var deletedIds = request.getDeletedIds();
        if (items == null || deletedIds == null || items.size() > 200 || deletedIds.size() > 200
                || (items.isEmpty() && deletedIds.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请提交有效的明细变更，每类最多200条");
        }
        var ids = new HashSet<Long>();
        var prepared = new ArrayList<LifeFootprintEntity>();
        for (var item : items) {
            if (item == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "明细不能为空");
            var entity = prepare(item);
            if (!prepared.isEmpty() && (!entity.getStartDate().equals(prepared.getFirst().getStartDate())
                    || !entity.getEndDate().equals(prepared.getFirst().getEndDate()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "同批明细必须使用相同的起止日期");
            }
            if (item.getId() != null) {
                if (item.getId() <= 0 || !ids.add(item.getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "明细ID无效或重复");
                }
                require(userId, item.getId());
                entity.setId(item.getId());
            }
            entity.setUserId(userId);
            prepared.add(entity);
        }
        for (var id : deletedIds) {
            if (id == null || id <= 0 || !ids.add(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "删除ID无效、重复或与保存明细冲突");
            }
            require(userId, id);
        }
        // 所有日期、地区和归属先通过校验；写入失败由外层事务一起回滚。
        for (var id : deletedIds) delete(userId, id);
        var saved = new ArrayList<LifeFootprintVO>();
        for (var entity : prepared) {
            if (entity.getId() == null) {
                entity.fillCreateCommonField(userId);
                mapper.insert(entity);
            } else {
                entity.fillUpdateCommonField(userId);
                if (mapper.update(entity, new LambdaUpdateWrapper<LifeFootprintEntity>()
                        .eq(LifeFootprintEntity::getId, entity.getId())
                        .eq(LifeFootprintEntity::getUserId, userId)) != 1) throw notFound();
            }
            saved.add(LifeFootprintConvertor.INSTANCE.toVO(entity));
        }
        return saved;
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
        entity.setRemark(trim(request.getRemark()));
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
