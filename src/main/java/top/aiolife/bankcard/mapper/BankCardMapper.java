package top.aiolife.bankcard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.*;
import top.aiolife.bankcard.pojo.entity.BankCardEntity;
import java.util.List;
import top.aiolife.bankcard.pojo.vo.BankCardOrderVO;

/** 银行卡持久化；所有用户操作都保留归属条件。 */
@Mapper
public interface BankCardMapper extends BaseMapper<BankCardEntity> {
    /** 仅锁定排序数据，按主键加锁以串行化同一范围内的拖动。 */
    @Select("SELECT id,sort_order FROM bank_card WHERE user_id=#{userId} AND is_deleted=0 ORDER BY id FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    List<BankCardOrderVO> lockOrder(@Param("userId") long userId);

    @Update("UPDATE bank_card SET sort_order=#{sortOrder},update_user=#{userId},update_time=CURRENT_TIMESTAMP WHERE id=#{id} AND user_id=#{userId} AND is_deleted=0")
    int updateOrder(@Param("userId") long userId, @Param("id") long id, @Param("sortOrder") int sortOrder);

    default List<BankCardEntity> list(long userId) {
        return selectList(Wrappers.<BankCardEntity>lambdaQuery()
                .eq(BankCardEntity::getUserId, userId)
                .orderByAsc(BankCardEntity::getSortOrder).orderByDesc(BankCardEntity::getId));
    }

    default BankCardEntity owned(long userId, long id) {
        var card = selectOne(Wrappers.<BankCardEntity>lambdaQuery()
                .eq(BankCardEntity::getUserId, userId).eq(BankCardEntity::getId, id));
        if (card == null) throw new IllegalArgumentException("银行卡不存在或无权访问");
        return card;
    }

    default void save(BankCardEntity card, boolean creating) {
        int affected = creating ? insert(card) : update(card, Wrappers.<BankCardEntity>lambdaUpdate()
                .eq(BankCardEntity::getId, card.getId()).eq(BankCardEntity::getUserId, card.getUserId()));
        if (affected != 1) throw new IllegalStateException("银行卡保存失败");
    }

    default boolean duplicate(long userId, long id, byte[] fingerprint) {
        return selectCount(Wrappers.<BankCardEntity>lambdaQuery()
                .eq(BankCardEntity::getUserId, userId).eq(BankCardEntity::getCardNoFingerprint, fingerprint)
                .ne(BankCardEntity::getId, id)) > 0;
    }

    /** 逻辑删除同时记录操作人和时间。 */
    @Update("UPDATE bank_card SET is_deleted=1,update_user=#{userId},update_time=CURRENT_TIMESTAMP WHERE id=#{id} AND user_id=#{userId} AND is_deleted=0")
    int softDeleteOwned(@Param("userId") long userId, @Param("id") long id);

    /** 当前读必须访问数据库，不能复用同一 SqlSession 中的查询缓存。 */
    @Select("SELECT id FROM bank_card WHERE cover_template_id=#{templateId} AND is_deleted=0 FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    List<Long> lockTemplateReferences(@Param("templateId") long templateId);

    @Select("SELECT id FROM bank_card WHERE bank_id=#{bankId} AND is_deleted=0 FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    List<Long> lockBankReferences(@Param("bankId") long bankId);
}
