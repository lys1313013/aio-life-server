package top.aiolife.bankcard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.*;
import top.aiolife.bankcard.pojo.entity.BankCardTagRelEntity;
import java.util.List;

@Mapper
public interface BankCardTagRelMapper extends BaseMapper<BankCardTagRelEntity> {
    default List<BankCardTagRelEntity> listByUser(long userId) {
        return selectList(Wrappers.<BankCardTagRelEntity>lambdaQuery()
                .eq(BankCardTagRelEntity::getUserId, userId).orderByAsc(BankCardTagRelEntity::getId));
    }

    @Select("SELECT tag_id FROM bank_card_tag_rel WHERE bank_card_id=#{cardId} AND user_id=#{userId} AND is_deleted=0")
    List<Long> selectTagIds(@Param("cardId") long cardId, @Param("userId") long userId);

    @Update("UPDATE bank_card_tag_rel SET is_deleted=1,update_user=#{userId},update_time=CURRENT_TIMESTAMP WHERE bank_card_id=#{cardId} AND user_id=#{userId} AND is_deleted=0")
    int deleteByCard(@Param("userId") long userId, @Param("cardId") long cardId);

    @Update("UPDATE bank_card_tag_rel SET is_deleted=1,update_user=#{userId},update_time=CURRENT_TIMESTAMP WHERE tag_id=#{tagId} AND user_id=#{userId} AND is_deleted=0")
    int deleteByTag(@Param("userId") long userId, @Param("tagId") long tagId);
}
