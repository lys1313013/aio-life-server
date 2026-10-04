package top.aiolife.bankcard.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.bankcard.mapper.BankCardCoverTemplateMapper;
import top.aiolife.bankcard.mapper.BankCardMapper;
import top.aiolife.bankcard.pojo.req.BankCardMoveReq;
import top.aiolife.bankcard.pojo.vo.BankCardOrderVO;

@Service
@RequiredArgsConstructor
public class BankCardOrderService {
    private final BankCardMapper cards;
    private final BankCardCoverTemplateMapper covers;

    @Transactional(rollbackFor = Exception.class)
    public List<BankCardOrderVO> moveCard(long userId, BankCardMoveReq req) {
        var original = cards.lockOrder(userId);
        var changes = move(original, req, Comparator.reverseOrder());
        for (var change : changed(original, changes)) {
            if (cards.updateOrder(userId, change.id(), change.sortOrder()) != 1)
                throw new IllegalStateException("排序保存失败，请重试");
        }
        return changes;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<BankCardOrderVO> moveCover(long userId, BankCardMoveReq req) {
        var original = covers.lockOrder();
        var changes = move(original, req, Comparator.naturalOrder());
        for (var change : changed(original, changes)) {
            if (covers.updateOrder(userId, change.id(), change.sortOrder()) != 1)
                throw new IllegalStateException("排序保存失败，请重试");
        }
        return changes;
    }

    private List<BankCardOrderVO> changed(List<BankCardOrderVO> original, List<BankCardOrderVO> ordered) {
        var ranks = original.stream().collect(Collectors.toMap(BankCardOrderVO::id, BankCardOrderVO::sortOrder));
        return ordered.stream().filter(row -> !row.sortOrder().equals(ranks.get(row.id()))).toList();
    }

    private List<BankCardOrderVO> move(List<BankCardOrderVO> rows, BankCardMoveReq req,
                                      Comparator<Long> idOrder) {
        var ordered = new ArrayList<>(rows);
        ordered.sort(Comparator.comparing(BankCardOrderVO::sortOrder)
                .thenComparing(BankCardOrderVO::id, idOrder));
        var moving = ordered.stream().filter(row -> row.id().equals(req.id())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("卡片不存在或无权访问"));
        var target = ordered.stream().filter(row -> row.id().equals(req.targetId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("目标卡片不存在或无权访问"));
        if (moving.id().equals(target.id())) return ordered;
        ordered.remove(moving);
        ordered.add(ordered.indexOf(target) + (req.after() ? 1 : 0), moving);
        var changes = new ArrayList<BankCardOrderVO>();
        // 依据完整范围分配唯一序号，保留未加载和筛选外卡片的相对顺序。
        for (int index = 0; index < ordered.size(); index++) {
            var row = ordered.get(index);
            changes.add(new BankCardOrderVO(row.id(), index));
        }
        return changes;
    }
}
