package top.aiolife.sso.autotask;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.service.UserLastActiveStore;

import static org.mockito.Mockito.*;
import static top.aiolife.support.MockMatchers.anyWrapper;

class UserLastActiveSyncTaskTest {
    @Test void 单条写库失败保留待重试并继续确认其余用户() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "active"), UserEntity.class);
        var store = mock(UserLastActiveStore.class);
        var users = mock(UserMapper.class);
        var task = new UserLastActiveSyncTask(store, users);
        ReflectionTestUtils.setField(task, "batchSize", 200L);
        when(store.pending(200)).thenReturn(List.of("1", "2"), List.of("1"));
        when(store.timestamps(List.of("1", "2"))).thenReturn(List.of("1000", "2000"));
        when(store.timestamps(List.of("1"))).thenReturn(List.of("1000"));
        when(users.update(anyWrapper())).thenThrow(new IllegalStateException("模拟数据库故障")).thenReturn(1);

        task.syncToDb();
        verify(store, never()).acknowledge("1", "1000");
        verify(store).acknowledge("2", "2000");

        task.syncToDb();
        verify(store).acknowledge("1", "1000");
    }
}
