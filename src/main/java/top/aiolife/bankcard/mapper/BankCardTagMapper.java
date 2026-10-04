package top.aiolife.bankcard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.*;
import top.aiolife.bankcard.pojo.vo.BankCardVO;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import java.time.LocalDateTime;
import java.util.List;

/** 银行卡私有标签复用用户字典实体，查询始终限制用户及字典类型。 */
@Mapper
public interface BankCardTagMapper extends BaseMapper<UserDictDataEntity> {
    @Select("SELECT id,dict_label AS name,color,status FROM user_dict_data WHERE user_id=#{userId} AND dict_type='bank_card_tag' AND is_deleted=0 ORDER BY dict_sort,id")
    List<BankCardVO.Tag> selectTags(@Param("userId") long userId);

    default long countDuplicate(long userId, String name, long excludedId) {
        return selectCount(Wrappers.<UserDictDataEntity>lambdaQuery()
                .eq(UserDictDataEntity::getUserId, userId).eq(UserDictDataEntity::getDictType, "bank_card_tag")
                .eq(UserDictDataEntity::getDictLabel, name).ne(UserDictDataEntity::getId, excludedId));
    }

    default int updateTag(long userId, long id, String name, String color, String status) {
        // 显式 set 保证 null 颜色可以被清空。
        return update(Wrappers.<UserDictDataEntity>lambdaUpdate()
                .eq(UserDictDataEntity::getId, id).eq(UserDictDataEntity::getUserId, userId)
                .eq(UserDictDataEntity::getDictType, "bank_card_tag")
                .set(UserDictDataEntity::getDictLabel, name).set(UserDictDataEntity::getColor, color)
                .set(UserDictDataEntity::getStatus, status).set(UserDictDataEntity::getUpdateUser, userId)
                .set(UserDictDataEntity::getUpdateTime, LocalDateTime.now()));
    }

    @Update("UPDATE user_dict_data SET is_deleted=1,update_user=#{userId},update_time=CURRENT_TIMESTAMP WHERE id=#{id} AND user_id=#{userId} AND dict_type='bank_card_tag' AND is_deleted=0")
    int deleteTag(@Param("userId") long userId, @Param("id") long id);
}
