package top.aiolife.sso.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import top.aiolife.core.resq.PageResp;
import top.aiolife.sso.pojo.entity.MessageEntity;

import java.util.List;

public interface IMessageService extends IService<MessageEntity> {

    List<MessageEntity> listByUserId(Long userId, Boolean isRead);

    IPage<MessageEntity> pageByUserId(Long userId, Boolean isRead, long current, long size);

    /** 当前用户消息分页，页大小上限 50。 */
    PageResp<MessageEntity> pageForUser(long userId, Boolean isRead, Integer page, Integer pageSize);

    void markAsRead(Long messageId, Long userId);

    void markAllAsRead(Long userId);

    long getUnreadCount(Long userId);

    MessageEntity createMessage(MessageEntity message);

    /** 用户创建消息，发送人与审计字段固定为当前用户。 */
    MessageEntity createForUser(long userId, MessageEntity message);

    /** 管理员发送消息，发送人与审计字段固定为管理员。 */
    MessageEntity createByAdmin(long adminId, MessageEntity message);

    IPage<MessageEntity> adminList(Long current, Long size, Long userId);

    /** 管理员消息分页，页大小上限 200。 */
    PageResp<MessageEntity> adminPage(Integer page, Integer size, Long userId);

    void adminDelete(Long messageId);

    void deleteMessage(Long id, Long userId);
}
