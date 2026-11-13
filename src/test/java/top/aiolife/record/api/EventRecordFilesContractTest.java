package top.aiolife.record.api;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.aiolife.record.mapper.IEventRecordMapper;
import top.aiolife.record.pojo.entity.EventRecordEntity;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.pojo.req.EventRecordUpdateReq;
import top.aiolife.record.service.EventRecordService;
import top.aiolife.record.service.IFileService;

import static top.aiolife.support.MockMatchers.anyWrapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
class EventRecordFilesContractTest {
 @BeforeAll static void initializeTables(){var assistant=new MapperBuilderAssistant(new MybatisConfiguration(),"event-record-test");TableInfoHelper.initTableInfo(assistant,EventRecordEntity.class);TableInfoHelper.initTableInfo(assistant,FileEntity.class);}
 void asUser(Runnable action){var original=StpUtil.getStpLogic();StpUtil.setStpLogic(new StpLogic("login"){@Override public long getLoginIdAsLong(){return 1L;}});try{action.run();}finally{StpUtil.setStpLogic(original);}}
 EventRecordUpdateReq payload(List<String> ids){var entity=new EventRecordUpdateReq();entity.setId(2L);entity.setFileIds(ids);return entity;}
 @Test void testUpdate_未传列表保留附件(){var mapper=mock(IEventRecordMapper.class);var files=mock(IFileService.class);when(mapper.update(any(EventRecordEntity.class),any())).thenReturn(1);asUser(()->new EventRecordController(new EventRecordService(mapper,files)).updateEventRecord("1", payload(null)));verifyNoInteractions(files);}
 @Test void testUpdate_空列表解除当前用户活动关联(){var mapper=mock(IEventRecordMapper.class);var files=mock(IFileService.class);when(mapper.update(any(EventRecordEntity.class),any())).thenReturn(1);asUser(()->new EventRecordController(new EventRecordService(mapper,files)).updateEventRecord("1", payload(List.of())));ArgumentCaptor<Wrapper<FileEntity>> captor=ArgumentCaptor.captor();verify(files).update(captor.capture());assertTrue(captor.getValue().getSqlSegment().contains("biz_type ="));assertTrue(captor.getValue().getSqlSegment().contains("biz_id ="));assertTrue(captor.getValue().getSqlSegment().contains("create_user ="));assertFalse(captor.getValue().getSqlSegment().contains("NOT IN"));assertTrue(((AbstractWrapper<?,?,?>)captor.getValue()).getParamNameValuePairs().values().containsAll(List.of("event_record",2L,1L)));verify(files).bindBizId(List.of(),"event_record",2L);}
 @Test void testUpdate_替换附件先核验用户归属再移除缺失附件(){var mapper=mock(IEventRecordMapper.class);var files=mock(IFileService.class);when(mapper.update(any(EventRecordEntity.class),any())).thenReturn(1);when(files.list(anyWrapper())).thenReturn(List.of(new FileEntity(),new FileEntity()));asUser(()->new EventRecordController(new EventRecordService(mapper,files)).updateEventRecord("1", payload(List.of("31","32","31"))));ArgumentCaptor<Wrapper<FileEntity>> captor=ArgumentCaptor.captor();verify(files).list(captor.capture());assertTrue(captor.getValue().getSqlSegment().contains("create_user ="));verify(files).update(captor.capture());assertTrue(captor.getValue().getSqlSegment().contains("NOT IN"));verify(files).bindBizId(List.of("31","32"),"event_record",2L);}
 @Test void testUpdate_无权活动不访问附件(){var mapper=mock(IEventRecordMapper.class);var files=mock(IFileService.class);asUser(()->assertThrows(IllegalArgumentException.class,()->new EventRecordController(new EventRecordService(mapper,files)).updateEventRecord("1", payload(List.of("99")))));verifyNoInteractions(files);}
 @Test void testUpdate_外人附件拒绝且不解除旧关联(){var mapper=mock(IEventRecordMapper.class);var files=mock(IFileService.class);when(mapper.update(any(EventRecordEntity.class),any())).thenReturn(1);when(files.list(anyWrapper())).thenReturn(List.of());asUser(()->assertThrows(IllegalArgumentException.class,()->new EventRecordController(new EventRecordService(mapper,files)).updateEventRecord("1", payload(List.of("99")))));verify(files,never()).update(any());verify(files,never()).bindBizId(any(),any(),any());}
}
