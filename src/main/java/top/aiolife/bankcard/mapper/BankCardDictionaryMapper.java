package top.aiolife.bankcard.mapper;

import org.apache.ibatis.annotations.*;
import top.aiolife.bankcard.pojo.vo.BankCardVO;
import java.util.List;

/** 系统银行字典查询及共享行锁。 */
@Mapper
public interface BankCardDictionaryMapper {
    @Select("SELECT d.dict_code AS id,d.dict_label AS name,d.dict_value AS code,(d.status='0' AND t.status='0') AS enabled FROM sys_dict_data d JOIN sys_dict_type t ON t.dict_id=d.dict_id WHERE t.dict_type='bank' AND t.is_deleted=0 AND d.is_deleted=0 ORDER BY d.dict_sort,d.dict_code")
    List<BankCardVO.Bank> selectBanks();

    @Select("SELECT id FROM `user` WHERE id=#{userId} AND is_deleted=0 FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    Long lockUser(@Param("userId") long userId);

    @Select("SELECT dict_id FROM sys_dict_type WHERE dict_id=#{typeId} FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    Long lockType(@Param("typeId") long typeId);

    @Select("SELECT dict_id FROM sys_dict_data WHERE dict_code=#{bankId} AND is_deleted=0")
    Long selectTypeId(@Param("bankId") long bankId);

    @Select("SELECT dict_type FROM sys_dict_type WHERE dict_id=#{typeId}")
    String selectType(@Param("typeId") long typeId);

    @Select("SELECT t.dict_id FROM sys_dict_type t JOIN sys_dict_data d ON d.dict_id=t.dict_id WHERE d.dict_code=#{bankId} AND t.dict_type='bank' AND t.is_deleted=0 AND d.is_deleted=0")
    Long selectBankTypeId(@Param("bankId") long bankId);

    @Select("SELECT (d.status='0' AND t.status='0') FROM sys_dict_data d JOIN sys_dict_type t ON t.dict_id=d.dict_id WHERE d.dict_code=#{bankId} AND t.dict_type='bank' AND d.is_deleted=0 AND t.is_deleted=0 FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    Boolean lockBankEnabled(@Param("bankId") long bankId);

    @Select("SELECT COUNT(*) FROM sys_dict_data d JOIN sys_dict_type t ON t.dict_id=d.dict_id WHERE d.dict_code=#{bankId} AND t.dict_type='bank' AND d.is_deleted=0 AND t.is_deleted=0 AND d.status='0' AND t.status='0'")
    long countEnabledBank(@Param("bankId") long bankId);

    @Select("SELECT dict_id FROM sys_dict_type WHERE dict_type='bank' AND is_deleted=0 ORDER BY dict_id")
    List<Long> selectBankTypeIds();
}
