package top.aiolife.record.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.imageio.ImageIO;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import top.aiolife.bankcard.mapper.BankCardFileMapper;
import top.aiolife.core.lock.StorageObjectLock;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.api.BVideoController;
import top.aiolife.record.mapper.*;
import top.aiolife.record.pojo.entity.*;
import top.aiolife.record.pojo.req.BVideoCreateReq;
import top.aiolife.record.pojo.req.BVideoProgressReq;
import top.aiolife.record.pojo.req.BVideoUpdateReq;
import top.aiolife.sso.service.AccountStatusGuard;
import top.aiolife.sso.service.SecondaryLockGuard;
import top.aiolife.system.mapper.StorageObjectMapper;
import top.aiolife.system.pojo.entity.StorageObjectEntity;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 实际 Mapper/数据库事务，外网与 MinIO 使用替身。 */
class VideoCoverServiceTest {
    JdbcTemplate jdbc;
    VideoCoverService service;
    IBVideoMapper videos;
    ImageImportTaskMapper tasks;
    IFileMapper files;
    StorageObjectMapper objects;
    MinioUtil minio;
    DataSourceTransactionManager tx;
    byte[] png;
    final String URL = "http://i1.hdslb.com/bfs/archive/example.jpg";

    @BeforeEach void setup() throws Exception {
        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:cover_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds); tx = new DataSourceTransactionManager(ds);
        // 从真实全量结构截取本功能四张表，去除 H2 不支持的 MySQL 排序规则及前缀索引。
        String schema = Files.readString(Path.of("sql/1_init_table/2026-08-18_init_all_tables.sql"));
        for (String table : List.of("b_video", "file", "storage_object", "image_import_task")) {
            int start = schema.indexOf("CREATE TABLE IF NOT EXISTS `"+table+"`");
            String sql = schema.substring(start, schema.indexOf(";",start));
            sql = sql.replaceAll(" COLLATE [a-zA-Z0-9_]+", "").replaceAll(" CHARACTER SET [a-zA-Z0-9_]+", "")
                    .replace("`title`(100)", "`title`");
            jdbc.execute(sql);
        }
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.addMapper(IBVideoMapper.class); config.addMapper(IFileMapper.class);
        config.addMapper(StorageObjectMapper.class); config.addMapper(ImageImportTaskMapper.class);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config);
        var session = new SqlSessionTemplate(factory.getObject());
        videos = session.getMapper(IBVideoMapper.class); files = session.getMapper(IFileMapper.class);
        objects = session.getMapper(StorageObjectMapper.class); tasks = session.getMapper(ImageImportTaskMapper.class);
        minio = mock(MinioUtil.class);
        Set<String> stored = new HashSet<>();
        when(minio.objectExists(anyString(),anyString())).thenAnswer(c -> stored.contains(c.getArgument(1)));
        doAnswer(c -> { stored.add(c.getArgument(1)); return null; }).when(minio).putObject(anyString(),anyString(),any(),anyLong(),anyString());
        var storage = proxy(new VideoCoverStorage(objects, mock(StorageObjectLock.class), minio));
        service = proxy(new VideoCoverService(videos,tasks,files,new VideoCoverDownloader(),storage));
        var bytes = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",bytes); png=bytes.toByteArray();
    }
    @SuppressWarnings("unchecked") <T> T proxy(T target) {
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource())); return (T)proxy.getProxy();
    }
    void video(long id,long owner) {
        jdbc.update("INSERT INTO b_video(id,title,url,duration,user_id,cover,create_user,update_user) VALUES (?,?,?,?,?,?,?,?)", id,"合成视频","https://www.bilibili.com/video/BVtest",60,owner,URL,owner,owner);
    }
    void finishNext() {
        var task=tasks.due().getFirst(); assertEquals(1,tasks.claim(task.getId(),"lease")); service.complete(task.getId(),"lease",png);
    }
    @Test void 不同用户相同字节只存一份但文件引用不同() throws Exception {
        video(1,11); video(2,22);
        service.enqueue(1,11,URL,false); service.enqueue(2,22,URL,false); finishNext(); finishNext();
        assertEquals(1,objects.selectCount(null)); assertEquals(2,files.selectCount(null));
        var a=videos.selectById(1L); var b=videos.selectById(2L);
        assertNotEquals(a.getCoverFileId(),b.getCoverFileId()); assertEquals(URL,a.getCover());
        assertEquals(files.selectById(a.getCoverFileId()).getStorageObjectId(),files.selectById(b.getCoverFileId()).getStorageObjectId());
        verify(minio,times(1)).putObject(eq("system"),matches("bvedio/[a-f0-9]{64}\\.png"),any(),eq((long)png.length),eq("image/png"));
        assertEquals(0,videos.countCoverReference(1,22,a.getCoverFileId()));
        videos.deleteById(1L); assertEquals(1,videos.countCoverReference(2,22,b.getCoverFileId()));
        assertEquals(1,objects.selectCount(null)); verify(minio,never()).removeObject(anyString(),anyString());
    }
    @Test void 旧任务不能覆盖更新后的封面且清空会撤销引用() {
        video(1,11); service.enqueue(1,11,URL,false); var old=tasks.due().getFirst(); tasks.claim(old.getId(),"old");
        service.enqueue(1,11,"https://i0.hdslb.com/bfs/archive/new.jpg",false);
        service.complete(old.getId(),"old",png); assertEquals("CANCELLED",tasks.selectById(old.getId()).getState());
        assertNull(videos.selectById(1L).getCoverFileId()); finishNext();
        service.enqueue(1,11,"",false); assertNull(videos.selectById(1L).getCoverFileId()); assertEquals("NONE",videos.selectById(1L).getCoverState());
    }
    @Test void 编辑相同来源不重复排队且事务回滚不留下任务() {
        video(1,11); service.enqueue(1,11,URL,false); service.enqueue(1,11,URL,false); assertEquals(1,tasks.selectCount(null));
        var template=new TransactionTemplate(tx);
        template.executeWithoutResult(s -> { service.enqueue(1,11,"https://i0.hdslb.com/bfs/archive/new.jpg",false); s.setRollbackOnly(); });
        assertEquals(1,tasks.selectCount(null)); assertEquals(URL,videos.selectById(1L).getCover());
    }
    @Test void 租约被重新领取后旧执行器不能写回且完成只生效一次() {
        video(1,11); service.enqueue(1,11,URL,false);
        var task=tasks.due().getFirst();
        assertEquals(1,tasks.claim(task.getId(),"first"));
        assertEquals(0,tasks.claim(task.getId(),"second"));
        jdbc.update("UPDATE image_import_task SET lease_until=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP)");
        assertEquals(1,tasks.claim(task.getId(),"second"));
        service.complete(task.getId(),"first",png); assertEquals(0,files.selectCount(null));
        service.complete(task.getId(),"second",png);
        service.complete(task.getId(),"second",png); assertEquals(1,files.selectCount(null));
    }
    @Test void 新增编辑及浏览器两个入口均使用相同导入任务() {
        var controller=proxy(new BVideoController(videos,service));
        try (var stp=mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            var create=new BVideoCreateReq();
            create.setTitle("测试"); create.setUrl("https://www.bilibili.com/video/BVtest");
            create.setDuration(60); create.setCover(URL); create.setBvid("BVone");
            assertEquals("0",controller.insert(create).getRscode());
            var first=videos.selectList(null).getFirst();
            assertEquals("PENDING",first.getCoverState()); assertEquals(1,tasks.selectCount(null));
            var update=new BVideoUpdateReq(); update.setTitle("仅修改标题");
            controller.update(first.getId(),update); assertEquals(1,tasks.selectCount(null));
            update.setCover("https://i0.hdslb.com/bfs/archive/replacement.jpg");
            controller.update(first.getId(),update); assertEquals(2,tasks.selectCount(null));
            create.setBvid("BVtwo"); controller.tagVideo(create); assertEquals(3,tasks.selectCount(null));
            var sync=new BVideoProgressReq();
            sync.setTitle("浏览器同步"); sync.setUrl(create.getUrl()); sync.setDuration(60);
            sync.setWatchedDuration(10); sync.setCurrentEpisode(1);
            sync.setBvid("BVthree"); sync.setCover(URL);
            controller.syncProgress(sync); assertEquals(4,tasks.selectCount(null));
            controller.syncProgress(sync); assertEquals(4,tasks.selectCount(null));
            sync.setCover(update.getCover()); controller.syncProgress(sync); assertEquals(5,tasks.selectCount(null));
            update.setCover(""); controller.update(first.getId(),update);
            assertEquals("NONE",videos.selectById(first.getId()).getCoverState());
        }
    }
    @Test void 最后一次执行中断可恢复成失败且允许显式重试() {
        video(1,11); service.enqueue(1,11,URL,false);
        jdbc.update("UPDATE image_import_task SET state='RUNNING',attempts=5,lease_until=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP),lease_token='dead'");
        var downloader=mock(VideoCoverDownloader.class); new VideoCoverWorker(tasks,downloader,service).runBatch();
        assertEquals("FAILED",videos.selectById(1L).getCoverState()); verifyNoInteractions(downloader);
        service.retry(1,11); assertEquals("PENDING",videos.selectById(1L).getCoverState()); assertEquals(2,tasks.selectCount(null));
    }
    @Test void 非属主不可重试且假装公开的文件也必须按业务授权() {
        video(1,11); service.enqueue(1,11,URL,false); finishNext();
        assertThrows(IllegalArgumentException.class,()->service.retry(1,22));
        var lock=mock(SecondaryLockGuard.class);
        var guard=new FilePreviewGuard(lock,mock(BankCardFileMapper.class),mock(AccountStatusGuard.class),videos);
        var file=files.selectById(videos.selectById(1L).getCoverFileId()); file.setIsPublic(1);
        assertEquals(FilePreviewGuard.AccessDecision.UNAUTHORIZED,guard.check(file,null));
        assertEquals(FilePreviewGuard.AccessDecision.FORBIDDEN,guard.check(file,22L));
        assertEquals(FilePreviewGuard.AccessDecision.ALLOW,guard.check(file,11L));
        videos.deleteById(1L); assertEquals(FilePreviewGuard.AccessDecision.FORBIDDEN,guard.check(file,11L));
    }
    @Test void 拒绝伪造域名内网及非图片内容() throws Exception {
        var d=new VideoCoverDownloader(); assertEquals("https://i1.hdslb.com/bfs/archive/example.jpg",d.normalize(URL));
        for (String source : List.of("https://hdslb.com.evil.test/bfs/a.jpg","https://hdslb.com@127.0.0.1/bfs/a.jpg","file:///etc/passwd","https://i1.hdslb.com:8080/bfs/a.jpg","https://i1.hdslb.com/bfs/a.jpg#x"))
            assertThrows(IllegalArgumentException.class,()->d.normalize(source));
        for (String address : List.of("127.0.0.1","10.0.0.1","169.254.169.254","100.64.0.1","::1","fc00::1"))
            assertFalse(VideoCoverDownloader.isPublicAddress(InetAddress.getByName(address)));
        video(1,11); service.enqueue(1,11,URL,false); var task=tasks.due().getFirst(); tasks.claim(task.getId(),"x");
        assertThrows(IllegalArgumentException.class,()->service.complete(task.getId(),"x","<html>bad</html>".getBytes()));
        assertEquals(0,objects.selectCount(null)); assertEquals(0,files.selectCount(null));
    }
}
