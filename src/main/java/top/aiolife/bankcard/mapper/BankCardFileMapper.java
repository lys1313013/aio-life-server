package top.aiolife.bankcard.mapper;

import org.apache.ibatis.annotations.*;
import top.aiolife.bankcard.pojo.dto.BankCardFileBinding;
import java.util.List;

/** 卡面文件的归属校验、行锁和审计更新；不负责物理文件删除。 */
@Mapper
public interface BankCardFileMapper {
    @Select("SELECT COUNT(*) FROM bank_card WHERE id=#{cardId} AND user_id=#{userId} AND is_deleted=0")
    long countOwnedCard(@Param("cardId") long cardId, @Param("userId") long userId);

    @Select("""
        SELECT COUNT(*) FROM bank_card_cover_template t
        JOIN sys_dict_data d ON d.id=t.bank_id
        JOIN sys_dict_type dt ON dt.id=d.dict_type_id
        WHERE t.id=#{templateId} AND t.is_deleted=0 AND (
          (t.is_enabled=1 AND d.is_enabled=1 AND d.is_deleted=0 AND dt.is_enabled=1 AND dt.is_deleted=0 AND dt.dict_type='bank')
          OR EXISTS(SELECT 1 FROM bank_card c WHERE c.cover_template_id=t.id AND c.user_id=#{userId} AND c.is_deleted=0))
        """)
    long countVisibleTemplate(@Param("templateId") long templateId, @Param("userId") long userId);

    @Select("SELECT file_key AS id,biz_id,create_user FROM attachment WHERE biz_type='bank_card_face' AND user_id=#{userId} AND is_deleted=0 AND is_public=0 AND biz_id IS NOT NULL")
    List<BankCardFileBinding> selectPrivateCovers(@Param("userId") long userId);

    @Select("SELECT file_key AS id,biz_id,create_user FROM attachment WHERE file_key=#{fileId} AND biz_type='bank_card_face' AND user_id=#{userId} AND is_public=0 AND is_deleted=0 FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    BankCardFileBinding lockPrivateCover(@Param("fileId") String fileId, @Param("userId") long userId);

    @Select("SELECT file_key AS id,biz_id,create_user FROM attachment WHERE file_key=#{fileId} AND biz_type='bank_card_template_face' AND is_deleted=0 FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    BankCardFileBinding lockTemplateCover(@Param("fileId") String fileId);

    @Select("SELECT COUNT(*) FROM attachment WHERE biz_type='bank_card_template_face' AND biz_id=#{templateId} AND is_deleted=0")
    long countTemplateFiles(@Param("templateId") long templateId);

    @Update("<script>UPDATE attachment SET is_deleted=1,update_user=#{userId},update_time=CURRENT_TIMESTAMP WHERE biz_type='bank_card_face' AND biz_id=#{cardId} AND user_id=#{userId} AND is_deleted=0 <if test='fileId != null'>AND file_key&lt;&gt;#{fileId}</if></script>")
    int deletePrivateCoversExcept(@Param("userId") long userId, @Param("cardId") long cardId, @Param("fileId") String fileId);

    @Update("UPDATE attachment SET biz_id=#{cardId},update_user=#{userId},update_time=CURRENT_TIMESTAMP WHERE file_key=#{fileId} AND user_id=#{userId} AND is_deleted=0")
    int bindPrivateCover(@Param("cardId") long cardId, @Param("userId") long userId, @Param("fileId") String fileId);

    @Update("<script>UPDATE attachment SET is_deleted=1,update_user=#{userId},update_time=CURRENT_TIMESTAMP WHERE biz_type='bank_card_template_face' AND biz_id=#{templateId} AND is_deleted=0 <if test='fileId != null'>AND file_key&lt;&gt;#{fileId}</if></script>")
    int deleteTemplateCoversExcept(@Param("userId") long userId, @Param("templateId") long templateId, @Param("fileId") String fileId);

    @Update("UPDATE attachment SET biz_id=#{templateId},is_public=1,update_user=#{userId},update_time=CURRENT_TIMESTAMP WHERE file_key=#{fileId}")
    int bindTemplateCover(@Param("templateId") long templateId, @Param("userId") long userId, @Param("fileId") String fileId);
    @Select("""
        SELECT count(*) FROM attachment a JOIN bank_card_cover_template t ON t.id=a.biz_id
        JOIN sys_dict_data d ON d.id=t.bank_id JOIN sys_dict_type dt ON dt.id=d.dict_type_id
        WHERE a.file_key=#{fileId} AND a.biz_type='bank_card_template_face' AND a.is_deleted=0 AND a.is_public=1
          AND t.is_deleted=0 AND t.is_enabled=1 AND d.is_deleted=0 AND d.is_enabled=1
          AND dt.is_deleted=0 AND dt.is_enabled=1 AND dt.dict_type='bank'
        """)
    long countPublishedTemplateFile(String fileId);
}
