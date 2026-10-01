package top.aiolife.record.api;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.aiolife.record.mapper.IPerformanceMapper;
import top.aiolife.record.pojo.entity.PerformanceEntity;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.service.IFileService;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
class PerformanceFilesContractTest {
 @BeforeAll static void initializeTables(){var assistant=new MapperBuilderAssistant(new MybatisConfiguration(),"performance-test");TableInfoHelper.initTableInfo(assistant,PerformanceEntity.class);TableInfoHelper.initTableInfo(assistant,FileEntity.class);}
 void asUser(Runnable action){var original=StpUtil.getStpLogic();StpUtil.setStpLogic(new StpLogic("login"){@Override public long getLoginIdAsLong(){return 1L;}});try{action.run();}finally{StpUtil.setStpLogic(original);}}
 PerformanceEntity payload(List<String> ids){var entity=new PerformanceEntity();entity.setId(2L);entity.setFileIds(ids);return entity;}
 @Test void testUpdate_未传列表保留附件(){var mapper=mock(IPerformanceMapper.class);var files=mock(IFileService.class);when(mapper.update(any(PerformanceEntity.class),any(Wrapper.class))).thenReturn(1);asUser(()->new PerformanceController(mapper,files).updatePerformance(payload(null)));verifyNoInteractions(files);}
 @Test void testUpdate_空列表解除当前用户活动关联(){var mapper=mock(IPerformanceMapper.class);var files=mock(IFileService.class);when(mapper.update(any(PerformanceEntity.class),any(Wrapper.class))).thenReturn(1);asUser(()->new PerformanceController(mapper,files).updatePerformance(payload(List.of())));var captor=ArgumentCaptor.forClass(Wrapper.class);verify(files).update(captor.capture());assertTrue(captor.getValue().getSqlSegment().contains("biz_type ="));assertTrue(captor.getValue().getSqlSegment().contains("biz_id ="));assertTrue(captor.getValue().getSqlSegment().contains("create_user ="));assertFalse(captor.getValue().getSqlSegment().contains("NOT IN"));assertTrue(((AbstractWrapper<?,?,?>)captor.getValue()).getParamNameValuePairs().values().containsAll(List.of("performance",2L,1L)));verify(files).bindBizId(List.of(),"performance",2L);}
 @Test void testUpdate_替换附件先核验用户归属再移除缺失附件(){var mapper=mock(IPerformanceMapper.class);var files=mock(IFileService.class);when(mapper.update(any(PerformanceEntity.class),any(Wrapper.class))).thenReturn(1);when(files.list(any(Wrapper.class))).thenReturn(List.of(new FileEntity(),new FileEntity()));asUser(()->new PerformanceController(mapper,files).updatePerformance(payload(List.of("31","32","31"))));var captor=ArgumentCaptor.forClass(Wrapper.class);verify(files).list(captor.capture());assertTrue(captor.getValue().getSqlSegment().contains("create_user ="));verify(files).update(captor.capture());assertTrue(captor.getValue().getSqlSegment().contains("NOT IN"));verify(files).bindBizId(List.of("31","32"),"performance",2L);}
 @Test void testUpdate_无权活动不访问附件(){var mapper=mock(IPerformanceMapper.class);var files=mock(IFileService.class);asUser(()->assertThrows(IllegalArgumentException.class,()->new PerformanceController(mapper,files).updatePerformance(payload(List.of("99")))));verifyNoInteractions(files);}
 @Test void testUpdate_外人附件拒绝且不解除旧关联(){var mapper=mock(IPerformanceMapper.class);var files=mock(IFileService.class);when(mapper.update(any(PerformanceEntity.class),any(Wrapper.class))).thenReturn(1);when(files.list(any(Wrapper.class))).thenReturn(List.of());asUser(()->assertThrows(IllegalArgumentException.class,()->new PerformanceController(mapper,files).updatePerformance(payload(List.of("99")))));verify(files,never()).update(any(Wrapper.class));verify(files,never()).bindBizId(any(),any(),any());}
}
