package top.aiolife.record.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.record.convertor.BodyProfileConvertor;
import top.aiolife.record.mapper.BodyProfileMapper;
import top.aiolife.record.pojo.entity.BodyProfileEntity;
import top.aiolife.record.pojo.req.BodyProfileSaveReq;
import top.aiolife.record.pojo.vo.BodyProfileVO;

@Service
@RequiredArgsConstructor
public class BodyProfileService {
    private final BodyProfileMapper mapper;

    public BodyProfileVO get(long userId) {
        var entity = find(userId);
        if (entity == null) {
            var empty = new BodyProfileVO();
            empty.setVersion(0);
            return empty;
        }
        return view(entity);
    }

    /** 完整替换可空字段；版本冲突拒绝覆盖，避免其他终端的新数据被旧表单改写。 */
    @Transactional(rollbackFor = Exception.class)
    public BodyProfileVO save(long userId, BodyProfileSaveReq request) {
        var current = find(userId);
        if (request.getVersion() == null || request.getVersion() != (current == null ? 0 : current.getVersion())) {
            throw conflict();
        }
        var entity = BodyProfileConvertor.INSTANCE.toEntity(request);
        entity.setUserId(userId);
        normalize(entity);
        if (current == null) {
            entity.setVersion(1);
            entity.fillCreateCommonField(userId);
            try {
                mapper.insert(entity);
            } catch (DuplicateKeyException exception) {
                throw conflict();
            }
        } else {
            entity.setId(current.getId());
            entity.setVersion(Math.incrementExact(current.getVersion()));
            entity.fillUpdateCommonField(userId);
            var wrapper = new LambdaUpdateWrapper<BodyProfileEntity>()
                    .eq(BodyProfileEntity::getUserId, userId)
                    .eq(BodyProfileEntity::getVersion, request.getVersion());
            if (mapper.update(entity, wrapper) != 1) throw conflict();
        }
        return view(entity);
    }

    private BodyProfileEntity find(long userId) {
        return mapper.selectOne(new LambdaQueryWrapper<BodyProfileEntity>().eq(BodyProfileEntity::getUserId, userId));
    }

    private BodyProfileVO view(BodyProfileEntity entity) {
        var response = BodyProfileConvertor.INSTANCE.toVO(entity);
        var height = entity.getHeightCm();
        var weight = entity.getWeightKg();
        if (height != null && weight != null && height.signum() > 0 && weight.signum() > 0) {
            response.setBmi(weight.multiply(BigDecimal.valueOf(10000))
                    .divide(height.multiply(height), 1, RoundingMode.HALF_UP));
        }
        return response;
    }

    private void normalize(BodyProfileEntity entity) {
        entity.setTopSize(trim(entity.getTopSize()));
        entity.setTrousersSize(trim(entity.getTrousersSize()));
        if (entity.getShoeSize() == null) {
            entity.setShoeSystem(null);
        } else if (entity.getShoeSystem() == null) {
            entity.setShoeSystem("EU");
        }
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "身体数据已在其他位置更新，请重新读取后再保存");
    }
}
