package top.aiolife.sso.service;

import static top.aiolife.support.MockMatchers.anyWrapper;
import static top.aiolife.support.MockMatchers.anyPage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import top.aiolife.sso.mapper.MessageMapper;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.sso.pojo.entity.MessageEntity;
import top.aiolife.sso.service.impl.MessageServiceImpl;

class MessagePaginationTest {

    @Test
    void 未读分页只查询当前用户接收的未读消息() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "message-pagination"),
                MessageEntity.class);
        MessageMapper mapper = mock(MessageMapper.class);
        MessageServiceImpl service = new MessageServiceImpl(mock(MinioUtil.class));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.selectPage(anyPage(), anyWrapper()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IPage<MessageEntity> result = service.pageByUserId(123L, false, 2, 20);

        assertEquals(2, result.getCurrent());
        assertEquals(20, result.getSize());
        ArgumentCaptor<Wrapper<MessageEntity>> query = ArgumentCaptor.captor();
        verify(mapper).selectPage(anyPage(), query.capture());
        String sql = query.getValue().getSqlSegment();
        Map<String, Object> values = ((LambdaQueryWrapper<MessageEntity>) query.getValue())
                .getParamNameValuePairs();
        assertTrue(sql.contains("receiver_id"));
        assertTrue(sql.contains("is_read"));
        assertTrue(sql.contains("create_time"));
        assertTrue(sql.contains("id"));
        assertFalse(sql.contains("sender_id"));
        assertTrue(values.containsValue(123L));
        assertTrue(values.containsValue(false));
    }

    @Test
    void 用户创建消息固定为用户类型并写入发送人() {
        MessageServiceImpl service = service();
        MessageEntity message = new MessageEntity();
        message.setType(0);
        message.setReceiverId(9L);

        service.createForUser(123L, message);

        assertEquals(1, message.getType());
        assertEquals(123L, message.getSenderId());
        assertEquals(123L, message.getCreateUser());
    }

    @Test
    void 管理员系统通知不写发送人() {
        MessageServiceImpl service = service();
        MessageEntity message = new MessageEntity();
        message.setType(0);
        message.setReceiverId(9L);

        service.createByAdmin(5L, message);

        assertNull(message.getSenderId());
        assertEquals(5L, message.getCreateUser());
    }

    @Test
    void 管理员用户消息写入管理员为发送人() {
        MessageServiceImpl service = service();
        MessageEntity message = new MessageEntity();
        message.setType(1);
        message.setReceiverId(9L);

        service.createByAdmin(5L, message);

        assertEquals(5L, message.getSenderId());
        assertEquals(5L, message.getCreateUser());
    }

    private MessageServiceImpl service() {
        MessageMapper mapper = mock(MessageMapper.class);
        when(mapper.insert(any(MessageEntity.class))).thenReturn(1);
        MessageServiceImpl service = new MessageServiceImpl(mock(MinioUtil.class));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        return service;
    }
}
