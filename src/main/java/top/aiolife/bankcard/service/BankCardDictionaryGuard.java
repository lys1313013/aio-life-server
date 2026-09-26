package top.aiolife.bankcard.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 银行字典维护和银行卡写入共享同一行锁，引用校验与修改处于同一事务。 */
@Service
@RequiredArgsConstructor
public class BankCardDictionaryGuard {
    private final JdbcTemplate jdbc;
    public void lockUser(long userId) {
        if (jdbc.queryForList("SELECT id FROM user WHERE id=? AND is_deleted=0 FOR UPDATE", Long.class, userId).isEmpty())
            throw new IllegalArgumentException("用户不存在");
    }
    public void lockType(long typeId) {
        jdbc.queryForList("SELECT dict_id FROM sys_dict_type WHERE dict_id=? FOR UPDATE", Long.class, typeId);
    }
    public void checkBankChange(long bankId, Long newTypeId, boolean deleting) {
        var ids = jdbc.queryForList("SELECT dict_id FROM sys_dict_data WHERE dict_code=? AND is_deleted=0", Long.class, bankId);
        if (ids.isEmpty()) return;
        lockType(ids.getFirst());
        var types = jdbc.queryForList("SELECT dict_type FROM sys_dict_type WHERE dict_id=?", String.class, ids.getFirst());
        if (!types.contains("bank")) return;
        if (deleting || (newTypeId != null && !newTypeId.equals(ids.getFirst()))) {
            var references = jdbc.queryForList("SELECT id FROM bank_card WHERE bank_id=? AND is_deleted=0 FOR UPDATE", Long.class, bankId);
            if (!references.isEmpty()) throw new IllegalArgumentException("银行已被银行卡引用，请停用而非删除或变更类型");
        }
    }
    public void checkTypeChange(long typeId, String newType, boolean deleting) {
        lockType(typeId);
        var types = jdbc.queryForList("SELECT dict_type FROM sys_dict_type WHERE dict_id=?", String.class, typeId);
        if (!types.contains("bank")) return;
        if (deleting || (newType != null && !"bank".equals(newType)))
            throw new IllegalArgumentException("银行字典类型用于银行卡模块，不能删除或变更标识，可停用");
    }
}
