package top.aiolife.record.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.aiolife.record.pojo.entity.FileEntity;

/** 公共图片仅来自已保存的模板或当前头像绑定，不接受任意公开文件。 */
@Mapper
public interface PublicImageMapper {
    @Select("""
        SELECT f.* FROM file f WHERE f.id=#{id} AND f.is_deleted=0
        AND f.storage_object_id IS NULL AND (
          (f.biz_type='bank_card_template_cover' AND EXISTS (
            SELECT 1 FROM bank_card_cover_template t WHERE t.id=f.biz_id AND t.is_deleted=0))
          OR (f.biz_type='avatar' AND f.is_public=1 AND EXISTS (
            SELECT 1 FROM `user` u WHERE u.id=f.create_user AND u.avatar_file_id=f.id AND u.is_deleted=0))
        )
        """)
    FileEntity selectPublished(@Param("id") String id);
}
