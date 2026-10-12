package top.aiolife.sso.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.aiolife.sso.mapper.MessageMapper;
import top.aiolife.sso.pojo.entity.MessageEntity;
import top.aiolife.sso.service.IMessageService;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl extends ServiceImpl<MessageMapper, MessageEntity> implements IMessageService {

    @Override
    public List<MessageEntity> listByUserId(Long userId, Boolean isRead) {
        return this.list(userMessageQuery(userId, isRead));
    }

    @Override
    public IPage<MessageEntity> pageByUserId(Long userId, Boolean isRead, long current, long size) {
        return this.page(new Page<>(current, size), userMessageQuery(userId, isRead));
    }

    private LambdaQueryWrapper<MessageEntity> userMessageQuery(Long userId, Boolean isRead) {
        LambdaQueryWrapper<MessageEntity> wrapper = new LambdaQueryWrapper<>();
        if (Boolean.FALSE.equals(isRead)) {
            // 通知场景：只查当前用户作为接收者的未读消息
            wrapper.eq(MessageEntity::getReceiverId, userId)
                    .eq(MessageEntity::getIsRead, false);
        } else {
            wrapper.and(w -> w.eq(MessageEntity::getReceiverId, userId)
                    .or()
                    .eq(MessageEntity::getSenderId, userId));
            if (isRead != null) {
                wrapper.eq(MessageEntity::getIsRead, isRead);
            }
        }
        return wrapper.orderByDesc(MessageEntity::getCreateTime)
                .orderByDesc(MessageEntity::getId);
    }

    @Override
    public void markAsRead(Long messageId, Long userId) {
        MessageEntity entity = this.getById(messageId);
        // 只有接收者可以标记为已读
        if (entity != null && entity.getReceiverId().equals(userId)) {
            entity.setIsRead(true);
            entity.setUpdateTime(LocalDateTime.now());
            this.updateById(entity);
        }
    }

    @Override
    public void markAllAsRead(Long userId) {
        // 标记所有发送给该用户的未读消息
        List<MessageEntity> messages = this.list(new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getReceiverId, userId)
                .eq(MessageEntity::getIsRead, false));

        if (messages.isEmpty()) {
            return;
        }

        for (MessageEntity message : messages) {
            message.setIsRead(true);
            message.setUpdateTime(LocalDateTime.now());
        }
        this.updateBatchById(messages);
    }

    @Override
    public long getUnreadCount(Long userId) {
        // 统计作为接收者的未读消息
        return this.count(new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getReceiverId, userId)
                .eq(MessageEntity::getIsRead, false));
    }

    @Override
    public MessageEntity createMessage(MessageEntity message) {
        message.setIsRead(false);
        message.setCreateTime(LocalDateTime.now());
        message.setUpdateTime(LocalDateTime.now());
        this.save(message);
        return message;
    }

    @Override
    public IPage<MessageEntity> adminList(Long current, Long size, Long userId) {
        Page<MessageEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<MessageEntity> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            // 管理员查询时，查询该用户参与的所有消息（发送或接收）
            wrapper.and(w -> w.eq(MessageEntity::getReceiverId, userId)
                    .or()
                    .eq(MessageEntity::getSenderId, userId));
        }
        return this.page(page, wrapper.orderByDesc(MessageEntity::getCreateTime));
    }

    @Override
    public void adminDelete(Long messageId) {
        this.removeById(messageId);
    }

    @Override
    public void deleteMessage(Long id, Long userId) {
        LambdaQueryWrapper<MessageEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageEntity::getId, id)
                // 用户只能删除自己参与的消息（发送或接收）
                .and(w -> w.eq(MessageEntity::getReceiverId, userId)
                        .or()
                        .eq(MessageEntity::getSenderId, userId));
        this.remove(wrapper);
    }
}
