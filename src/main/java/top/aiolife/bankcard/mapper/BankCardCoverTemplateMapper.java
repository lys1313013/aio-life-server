package top.aiolife.bankcard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.*;
import top.aiolife.bankcard.pojo.entity.BankCardCoverTemplateEntity;
import top.aiolife.bankcard.pojo.dto.BankCardTemplateCover;
import top.aiolife.bankcard.pojo.vo.BankCardCoverTemplateVO;
import java.util.List;

@Mapper
public interface BankCardCoverTemplateMapper extends BaseMapper<BankCardCoverTemplateEntity> {
    String DETAIL_SELECT = """
        SELECT t.id,t.name,t.bank_id,d.dict_label AS bank_name,t.card_type,t.source_url,
          t.is_enabled,t.sort_order,f.id AS file_id,
          (SELECT COUNT(*) FROM bank_card c WHERE c.cover_template_id=t.id AND c.is_deleted=0) AS usage_count
        FROM bank_card_cover_template t
        LEFT JOIN sys_dict_data d ON d.dict_code=t.bank_id
        LEFT JOIN file f ON f.biz_id=t.id AND f.biz_type='bank_card_template_cover' AND f.is_deleted=0
        WHERE t.is_deleted=0
        """;

    @Select(DETAIL_SELECT + " ORDER BY t.sort_order,t.id")
    List<BankCardCoverTemplateVO> selectDetails();

    @Select(DETAIL_SELECT + " AND t.id=#{id}")
    BankCardCoverTemplateVO selectDetail(@Param("id") long id);

    @Select("""
        SELECT t.id,t.name,f.id AS file_id FROM bank_card_cover_template t
        JOIN file f ON f.biz_id=t.id AND f.biz_type='bank_card_template_cover' AND f.is_deleted=0
        JOIN sys_dict_data d ON d.dict_code=t.bank_id AND d.is_deleted=0 AND d.status='0'
        JOIN sys_dict_type dt ON dt.dict_id=d.dict_id AND dt.dict_type='bank' AND dt.is_deleted=0 AND dt.status='0'
        WHERE t.bank_id=#{bankId} AND t.card_type=#{cardType} AND t.is_deleted=0 AND t.is_enabled=1
        ORDER BY t.sort_order,t.id
        """)
    List<BankCardCoverTemplateVO.Option> selectOptions(@Param("bankId") long bankId, @Param("cardType") String cardType);

    @Select("SELECT * FROM bank_card_cover_template WHERE id=#{id} AND is_deleted=0 FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    BankCardCoverTemplateEntity lockById(@Param("id") long id);

    @Select("SELECT id FROM bank_card_cover_template WHERE bank_id=#{bankId} AND is_deleted=0 FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    List<Long> lockBankReferences(@Param("bankId") long bankId);

    @Select("""
        SELECT DISTINCT t.id,t.name,t.source_url,f.id AS file_id FROM bank_card c
        JOIN bank_card_cover_template t ON t.id=c.cover_template_id AND t.is_deleted=0
        LEFT JOIN file f ON f.biz_id=t.id AND f.biz_type='bank_card_template_cover' AND f.is_deleted=0
        WHERE c.user_id=#{userId} AND c.is_deleted=0
        """)
    List<BankCardTemplateCover> selectUsedCovers(@Param("userId") long userId);

    default int setEnabled(long userId, long id, int enabled) {
        return update(Wrappers.<BankCardCoverTemplateEntity>lambdaUpdate()
                .eq(BankCardCoverTemplateEntity::getId, id)
                .set(BankCardCoverTemplateEntity::getIsEnabled, enabled)
                .set(BankCardCoverTemplateEntity::getUpdateUser, userId)
                .set(BankCardCoverTemplateEntity::getUpdateTime, java.time.LocalDateTime.now()));
    }

    @Update("UPDATE bank_card_cover_template SET is_deleted=1,update_user=#{userId},update_time=CURRENT_TIMESTAMP WHERE id=#{id} AND is_deleted=0")
    int softDelete(@Param("userId") long userId, @Param("id") long id);
}
