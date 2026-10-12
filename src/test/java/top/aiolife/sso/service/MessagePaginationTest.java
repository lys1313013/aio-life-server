package top.aiolife.sso.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import top.aiolife.sso.pojo.entity.MessageEntity;
import top.aiolife.sso.service.impl.MessageServiceImpl;

class MessagePaginationTest {

    @Test
    void 未读分页只查询当前用户接收的未读消息() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "message-pagination"),
                MessageEntity.class);
        MessageMapper mapper = mock(MessageMapper.class);
        MessageServiceImpl service = new MessageServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.selectPage(any(IPage.class), any(Wrapper.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IPage<MessageEntity> result = service.pageByUserId(123L, false, 2, 20);

        assertEquals(2, result.getCurrent());
        assertEquals(20, result.getSize());
        ArgumentCaptor<Wrapper<MessageEntity>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectPage(any(IPage.class), query.capture());
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
}
