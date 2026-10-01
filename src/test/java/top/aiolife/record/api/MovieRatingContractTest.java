package top.aiolife.record.api;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.aiolife.record.pojo.entity.MovieEntity;
import top.aiolife.record.pojo.req.MovieReq;
import top.aiolife.record.service.IFileService;
import top.aiolife.record.service.impl.MovieServiceImpl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
class MovieRatingContractTest {
 @BeforeAll static void initialize(){TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"movie-rating-test"),MovieEntity.class);}
 void asUser(Runnable action){var original=StpUtil.getStpLogic();StpUtil.setStpLogic(new StpLogic("login"){@Override public long getLoginIdAsLong(){return 1L;}});try{action.run();}finally{StpUtil.setStpLogic(original);}}
 MovieServiceImpl service(){var service=spy(new MovieServiceImpl(mock(IFileService.class)));var entity=new MovieEntity();entity.setId(2L);entity.setUserId(1L);entity.setRating(4);doReturn(entity).when(service).getById(2L);doReturn(true).when(service).update(any(MovieEntity.class),any(Wrapper.class));return service;}
 @Test void testUpdate_省略评分不生成评分更新()throws Exception{var req=new ObjectMapper().readValue("{\"id\":2,\"title\":\"模拟标题\"}",MovieReq.class);assertFalse(req.isRatingProvided());var service=service();asUser(()->service.updateRecord(req));var captor=ArgumentCaptor.forClass(Wrapper.class);verify(service).update(any(MovieEntity.class),captor.capture());assertNull(((LambdaUpdateWrapper<?>)captor.getValue()).getSqlSet());}
 @Test void testUpdate_显式空评分生成清空且限定用户()throws Exception{var req=new ObjectMapper().readValue("{\"id\":2,\"rating\":null}",MovieReq.class);assertTrue(req.isRatingProvided());var service=service();asUser(()->service.updateRecord(req));var captor=ArgumentCaptor.forClass(Wrapper.class);verify(service).update(any(MovieEntity.class),captor.capture());var wrapper=(LambdaUpdateWrapper<?>)captor.getValue();assertTrue(wrapper.getSqlSet().contains("rating="));assertTrue(wrapper.getParamNameValuePairs().containsValue(null));assertTrue(wrapper.getSqlSegment().contains("user_id ="));}
 @Test void testUpdate_有效评分写入而非法评分无写入()throws Exception{var req=new ObjectMapper().readValue("{\"id\":2,\"rating\":5}",MovieReq.class);var service=service();asUser(()->service.updateRecord(req));var captor=ArgumentCaptor.forClass(Wrapper.class);verify(service).update(any(MovieEntity.class),captor.capture());assertTrue(((LambdaUpdateWrapper<?>)captor.getValue()).getParamNameValuePairs().containsValue(5));var invalid=new MovieReq();invalid.setId(2L);invalid.setRating(6);var rejected=service();asUser(()->assertThrows(IllegalArgumentException.class,()->rejected.updateRecord(invalid)));verify(rejected,never()).update(any(MovieEntity.class),any(Wrapper.class));}
 @Test void testUpdate_外人记录无写入(){var req=new MovieReq();req.setId(2L);req.setRating(null);var service=service();var foreign=new MovieEntity();foreign.setUserId(99L);doReturn(foreign).when(service).getById(2L);asUser(()->assertThrows(RuntimeException.class,()->service.updateRecord(req)));verify(service,never()).update(any(MovieEntity.class),any(Wrapper.class));}
}
