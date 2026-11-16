package top.aiolife.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** 局部业务测试的附件夹具；真实BIGINT、UUID及约束另由PostgreSQL迁移测试验证。 */
public final class AttachmentTestFixtures {
    private AttachmentTestFixtures() {}
    /** 测试固定标识：不使用生产生成器，便于断言引用在多次调用间不变。 */
    public static String key(String label) {
        var value = new StringBuilder(java.util.UUID.nameUUIDFromBytes(label.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
        value.setCharAt(14, '4');
        return value.toString();
    }
    public static void create(JdbcTemplate jdbc) {
        jdbc.execute("""
            CREATE TABLE attachment(id VARCHAR(32) PRIMARY KEY,file_key VARCHAR(36) DEFAULT RANDOM_UUID() NOT NULL,storage_object_id BIGINT DEFAULT 11,external_resource_id BIGINT,
              file_name VARCHAR(255),biz_type VARCHAR(50),biz_id BIGINT,user_id BIGINT,sort_order INT DEFAULT 0,
              create_user BIGINT,update_user BIGINT,create_time TIMESTAMP,update_time TIMESTAMP,
              is_deleted INT DEFAULT 0,is_public INT DEFAULT 0)
            """);
        jdbc.execute("""
            CREATE TABLE storage_object(id BIGINT PRIMARY KEY,bucket VARCHAR(63),object_key VARCHAR(255),content_hash CHAR(64),
              file_size BIGINT,content_type VARCHAR(100),image_width INT,image_height INT,
              create_user BIGINT,update_user BIGINT,create_time TIMESTAMP,update_time TIMESTAMP,is_deleted INT DEFAULT 0)
            """);
        jdbc.update("INSERT INTO storage_object(id,bucket,object_key,content_hash,file_size,content_type) VALUES(11,'fixture','objects/aa/"+"a".repeat(64)+".png',?,68,'image/png')","a".repeat(64));
    }
}
