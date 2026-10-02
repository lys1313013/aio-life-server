package top.aiolife.bankcard.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.aiolife.bankcard.mapper.BankCardMapper;
import top.aiolife.bankcard.mapper.BankCardCoverTemplateMapper;
import top.aiolife.bankcard.mapper.BankCardDictionaryMapper;

/** 银行字典维护和银行卡写入共享同一行锁，引用校验与修改处于同一事务。 */
@Service
@RequiredArgsConstructor
public class BankCardDictionaryGuard {
    private final BankCardDictionaryMapper dictionaryMapper;
    private final BankCardMapper cardMapper;
    private final BankCardCoverTemplateMapper templateMapper;

    public void lockUser(long userId) {
        if (dictionaryMapper.lockUser(userId) == null)
            throw new IllegalArgumentException("用户不存在");
    }
    public void lockType(long typeId) {
        dictionaryMapper.lockType(typeId);
    }
    public void checkBankChange(long bankId, Long newTypeId, boolean deleting) {
        Long typeId = dictionaryMapper.selectTypeId(bankId);
        if (typeId == null) return;
        lockType(typeId);
        if (!"bank".equals(dictionaryMapper.selectType(typeId))) return;
        if (deleting || (newTypeId != null && !newTypeId.equals(typeId))) {
            if (!templateMapper.lockBankReferences(bankId).isEmpty())
                throw new IllegalArgumentException("银行已被公共卡面引用，请停用而非删除或变更类型");
            if (!cardMapper.lockBankReferences(bankId).isEmpty())
                throw new IllegalArgumentException("银行已被银行卡引用，请停用而非删除或变更类型");
        }
    }
    public void checkTypeChange(long typeId, String newType, boolean deleting) {
        lockType(typeId);
        if (!"bank".equals(dictionaryMapper.selectType(typeId))) return;
        if (deleting || (newType != null && !"bank".equals(newType)))
            throw new IllegalArgumentException("银行字典类型用于银行卡模块，不能删除或变更标识，可停用");
    }
}
