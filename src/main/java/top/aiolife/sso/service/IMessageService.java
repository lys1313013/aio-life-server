package top.aiolife.sso.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import top.aiolife.sso.pojo.entity.MessageEntity;

import java.util.List;

public interface IMessageService extends IService<MessageEntity> {

    List<MessageEntity> listByUserId(Long userId, Boolean isRead);

    IPage<MessageEntity> pageByUserId(Long userId, Boolean isRead, long current, long size);

    void markAsRead(Long messageId, Long userId);

    void markAllAsRead(Long userId);

    long getUnreadCount(Long userId);

    MessageEntity createMessage(MessageEntity message);

    IPage<MessageEntity> adminList(Long current, Long size, Long userId);

    void adminDelete(Long messageId);

    void deleteMessage(Long id, Long userId);
}
