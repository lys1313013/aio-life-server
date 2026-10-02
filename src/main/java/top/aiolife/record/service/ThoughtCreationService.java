package top.aiolife.record.service;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.record.mapper.IThoughtMapper;
import top.aiolife.record.mapper.IRelaEventMapper;
import top.aiolife.record.pojo.entity.ThoughtEntity;
import top.aiolife.record.pojo.entity.ThoughtRelaEventEntity;
import top.aiolife.record.pojo.req.ThoughtSaveReq;

/** HTTP 与 MCP 共用创建用例，校验和主从写入均在服务边界完成。 */
@Service
@RequiredArgsConstructor
public class ThoughtCreationService {
    private final IThoughtMapper thoughts;
    private final IRelaEventMapper events;
    private final Validator validator;

    @Transactional(rollbackFor = Exception.class)
    public void create(long userId, ThoughtSaveReq request) {
        if (request == null) throw new IllegalArgumentException("闪念内容不能为空");
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new IllegalArgumentException(violations.iterator().next().getMessage());
        var thought = new ThoughtEntity();
        thought.setContent(request.getContent());
        thought.setUserId(userId);
        thought.setIsPinned(request.getIsPinned() == null ? 0 : request.getIsPinned());
        thought.fillCreateCommonField(userId);
        if (thoughts.insert(thought) != 1) throw new IllegalStateException("闪念保存失败");
        if (request.getEvents() == null) return;
        for (var eventRequest : request.getEvents()) {
            var event = new ThoughtRelaEventEntity();
            event.setThoughtId(thought.getId());
            event.setContent(eventRequest.getContent());
            event.fillCreateCommonField(userId);
            if (events.insert(event) != 1) throw new IllegalStateException("关联事件保存失败");
        }
    }
}
