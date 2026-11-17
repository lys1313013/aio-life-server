package top.aiolife.record.mapper;

import org.apache.ibatis.annotations.*;
import top.aiolife.system.pojo.entity.StorageObjectEntity;

@Mapper
public interface PublicImageMapper {
    @Select("""
        SELECT o.* FROM storage_object o WHERE o.content_hash=#{hash} AND o.is_deleted=0
          AND EXISTS(SELECT 1 FROM attachment a WHERE a.storage_object_id=o.id AND a.is_deleted=0 AND a.is_public=1 AND (
            (a.biz_type='avatar' AND EXISTS(SELECT 1 FROM sys_user u WHERE u.id=a.biz_id AND u.id=a.user_id AND u.is_deleted=0))
            OR (a.biz_type='cbti_personality_image' AND a.user_id IS NULL AND EXISTS(
              SELECT 1 FROM cbti_personality p WHERE p.id=a.biz_id AND p.is_deleted=0))
            OR (a.biz_type='bank_card_template_face' AND a.user_id IS NULL AND EXISTS(
              SELECT 1 FROM bank_card_cover_template t JOIN sys_dict_data d ON d.id=t.bank_id
              JOIN sys_dict_type dt ON dt.id=d.dict_type_id AND dt.dict_type='bank'
              WHERE t.id=a.biz_id AND t.is_deleted=0 AND t.is_enabled=1
                AND d.is_deleted=0 AND d.is_enabled=1 AND dt.is_deleted=0 AND dt.is_enabled=1))))
        LIMIT 1
        """)
    StorageObjectEntity selectPublished(@Param("hash") String hash);
}
