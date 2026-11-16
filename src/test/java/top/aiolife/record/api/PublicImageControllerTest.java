package top.aiolife.record.api;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.mapper.PublicImageMapper;
import top.aiolife.record.service.PublicImageService;
import top.aiolife.support.AttachmentDatabaseFixture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static top.aiolife.support.ImageFixtures.image;

/** 真实PostgreSQL公开引用查询、MVC与ETag，外部对象存储隔离。 */
class PublicImageControllerTest extends AttachmentDatabaseFixture {
    private final String hash="a".repeat(64);
    private MinioUtil minio;
    private MockMvc mvc;
    @BeforeEach void setup() throws Exception {
        jdbc.execute("TRUNCATE bank_card_cover_template,sys_dict_data,sys_dict_type");
        minio=mock(MinioUtil.class);
        when(minio.getFile(eq("fixture"),anyString())).thenAnswer(c->new ByteArrayInputStream(image("png")));
        jdbc.update("INSERT INTO storage_object(id,bucket,object_key,content_hash,file_size,content_type) VALUES(11,'fixture',?, ?,68,'image/png')","objects/aa/"+hash+".png",hash);
        mvc=MockMvcBuilders.standaloneSetup(new PublicImageController(new PublicImageService(mapper(PublicImageMapper.class),minio))).build();
    }
    private String url() { return "/f/"+hash+".png"; }
    private void avatar(long id,long owner) {
        jdbc.update("INSERT INTO sys_user(id,username,nickname) VALUES(?,?,'fixture')",owner,"fixture"+owner);
        jdbc.update("INSERT INTO attachment(id,storage_object_id,user_id,biz_type,biz_id,is_public) VALUES(?,11,?,'avatar',?,1)",id,owner,owner);
    }
    private void template() {
        jdbc.update("INSERT INTO sys_dict_type(dict_id,dict_type,is_enabled) VALUES(1,'bank',1)");
        jdbc.update("INSERT INTO sys_dict_data(dict_code,dict_id,dict_label,dict_value,is_enabled) VALUES(2,1,'fixture','fixture',1)");
        jdbc.update("INSERT INTO bank_card_cover_template(id,name,bank_id,card_type,is_enabled,create_user,update_user) VALUES(3,'fixture',2,'debit',1,42,42)");
        jdbc.update("INSERT INTO attachment(id,storage_object_id,biz_type,biz_id,is_public) VALUES(4,11,'bank_card_template_face',3,1)");
    }
    @Test void 已发布模板可匿名访问并协商缓存不重读对象() throws Exception {
        template();
        var response=mvc.perform(get(url())).andExpect(status().isOk()).andExpect(content().contentType("image/png"))
            .andExpect(header().string("Cache-Control","public, no-cache")).andExpect(header().doesNotExist("Set-Cookie"))
            .andExpect(content().bytes(image("png"))).andReturn().getResponse();
        mvc.perform(get(url()).header("If-None-Match",response.getHeader("ETag"))).andExpect(status().isNotModified());
        verify(minio,times(1)).getFile(eq("fixture"),anyString());
    }
    @Test void 模板或银行停用后即使ETag命中也返回404() throws Exception {
        template();jdbc.update("UPDATE sys_dict_data SET is_enabled=0 WHERE dict_code=2");
        mvc.perform(get(url()).header("If-None-Match","\""+hash+"\"")).andExpect(status().isNotFound());
        verifyNoInteractions(minio);
    }
    @Test void 撤销一个头像不影响其他公开引用全部撤销后拒绝读取() throws Exception {
        avatar(101,41);avatar(102,42);jdbc.update("UPDATE attachment SET is_public=0,biz_id=NULL WHERE id=101");
        mvc.perform(get(url())).andExpect(status().isOk());
        jdbc.update("UPDATE attachment SET is_deleted=1 WHERE id=102");
        mvc.perform(get(url()).header("If-None-Match","\""+hash+"\"")).andExpect(status().isNotFound());
    }
    @Test void 私有附件和错误扩展名不能通过公开地址读取() throws Exception {
        jdbc.update("INSERT INTO attachment(id,storage_object_id,user_id,biz_type,biz_id,is_public) VALUES(101,11,42,'device_cover',1,1)");
        mvc.perform(get(url())).andExpect(status().isNotFound());
        avatar(102,42);mvc.perform(get("/f/"+hash+".jpg")).andExpect(status().isNotFound());
        verifyNoInteractions(minio);
    }
    @Test void 已删除用户的头像不再公开() throws Exception {
        avatar(101,42);jdbc.update("UPDATE sys_user SET is_deleted=1 WHERE id=42");
        mvc.perform(get(url())).andExpect(status().isNotFound());
    }
}
