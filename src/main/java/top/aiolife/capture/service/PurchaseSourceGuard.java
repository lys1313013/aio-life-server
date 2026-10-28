package top.aiolife.capture.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.aiolife.record.mapper.IDeviceMapper;
import top.aiolife.record.pojo.entity.DeviceEntity;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.wardrobe.mapper.WardrobeItemMapper;
import top.aiolife.wardrobe.pojo.entity.WardrobeItemEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 同一用户的订单采集通过用户行锁串行执行，来源在设备、衣物两表间共同查重。 */
@Service
@RequiredArgsConstructor
public class PurchaseSourceGuard {
    private final UserMapper users;
    private final IDeviceMapper devices;
    private final WardrobeItemMapper wardrobe;

    public record SourceUnit(String itemId, int unitIndex) {}

    public record Asset(String kind, String id, String productId) {}

    public Map<SourceUnit, List<Asset>> find(long user, JsonNode order, boolean lock) {
        String platform = order.path("platform").asText();
        String orderId = order.path("orderNo").asText();
        var result = new LinkedHashMap<SourceUnit, List<Asset>>();
        if (orderId.isBlank()) return result;
        if (lock && users.selectForAuthUpdate(user) == null) throw CaptureValues.missing();
        var deviceRows = lock ? devices.lockCaptureOrder(user, platform, orderId)
                : devices.selectList(new LambdaQueryWrapper<DeviceEntity>()
                    .eq(DeviceEntity::getUserId, user).eq(DeviceEntity::getSourcePlatform, platform)
                    .eq(DeviceEntity::getSourceOrderId, orderId));
        for (var row : deviceRows) add(result, row.getSourceItemId(), row.getSourceUnitIndex(),
                new Asset("device", row.getId().toString(), row.getSourceProductId()));
        var wardrobeRows = lock ? wardrobe.lockCaptureOrder(user, platform, orderId)
                : wardrobe.selectList(new LambdaQueryWrapper<WardrobeItemEntity>()
                    .eq(WardrobeItemEntity::getUserId, user).eq(WardrobeItemEntity::getSourcePlatform, platform)
                    .eq(WardrobeItemEntity::getSourceOrderId, orderId));
        for (var row : wardrobeRows) add(result, row.getSourceItemId(), row.getSourceUnitIndex(),
                new Asset("wardrobe", row.getId().toString(), row.getSourceProductId()));
        return result;
    }

    private void add(Map<SourceUnit, List<Asset>> result, String itemId, Integer unitIndex, Asset asset) {
        // 未记录完整来源的手工或历史物品不能据商品ID推断为某个明细件。
        if (itemId == null || itemId.isBlank() || unitIndex == null) return;
        result.computeIfAbsent(new SourceUnit(itemId, unitIndex), key -> new ArrayList<>()).add(asset);
    }
}
