package top.aiolife.bankcard.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.bankcard.mapper.BankCardRepository;
import top.aiolife.bankcard.pojo.entity.BankCardEntity;
import top.aiolife.bankcard.pojo.req.BankCardReq;
import top.aiolife.bankcard.pojo.req.BankCardTagReq;
import top.aiolife.bankcard.pojo.vo.BankCardVO;
import java.net.URI;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BankCardService {
    public static final String TAG_TYPE = "bank_card_tag";
    public static final String COVER_TYPE = "bank_card_cover";
    private final BankCardRepository repository;
    private final BankCardCrypto crypto;
    private final BankCardDictionaryGuard guard;
    private final JdbcTemplate jdbc;

    public List<BankCardVO.Bank> banks() {
        return jdbc.query("SELECT d.dict_code,d.dict_label,d.dict_value,d.status,t.status AS type_status FROM sys_dict_data d JOIN sys_dict_type t ON t.dict_id=d.dict_id WHERE t.dict_type='bank' AND t.is_deleted=0 AND d.is_deleted=0 ORDER BY d.dict_sort,d.dict_code",
                (rs,n) -> new BankCardVO.Bank(rs.getString("dict_code"), rs.getString("dict_label"), rs.getString("dict_value"),
                        "0".equals(rs.getString("status")) && "0".equals(rs.getString("type_status"))));
    }
    public List<BankCardVO.Tag> tags(long userId) {
        return jdbc.query("SELECT id,dict_label,color,status FROM user_dict_data WHERE user_id=? AND dict_type=? AND is_deleted=0 ORDER BY dict_sort,id",
                (rs,n) -> new BankCardVO.Tag(rs.getString("id"), rs.getString("dict_label"), rs.getString("color"), rs.getString("status")), userId, TAG_TYPE);
    }
    public List<BankCardVO> list(long userId) {
        var bankMap = new HashMap<String, BankCardVO.Bank>();
        banks().forEach(b -> bankMap.put(b.id(), b));
        var tagMap = new HashMap<String, BankCardVO.Tag>();
        tags(userId).forEach(t -> tagMap.put(t.id(), t));
        Map<Long, List<BankCardVO.Tag>> cardTags = new HashMap<>();
        jdbc.query("SELECT bank_card_id,tag_id FROM bank_card_tag_rel WHERE user_id=? AND is_deleted=0 ORDER BY id", rs -> {
            var tag = tagMap.get(rs.getString("tag_id"));
            if (tag != null) cardTags.computeIfAbsent(rs.getLong("bank_card_id"), k -> new ArrayList<>()).add(tag);
        }, userId);
        Map<Long, List<String>> covers = new HashMap<>();
        jdbc.query("SELECT id,biz_id FROM file WHERE biz_type=? AND create_user=? AND is_deleted=0 AND is_public=0 AND biz_id IS NOT NULL", rs -> {
            covers.computeIfAbsent(rs.getLong("biz_id"), k -> new ArrayList<>()).add(rs.getString("id"));
        }, COVER_TYPE, userId);
        return repository.list(userId).stream().map(card -> {
            var vo = new BankCardVO();
            BeanUtils.copyProperties(card, vo);
            vo.setId(card.getId().toString());
            vo.setBankId(card.getBankId() == null ? null : card.getBankId().toString());
            var bank = bankMap.get(vo.getBankId());
            vo.setBankName(card.getBankId() == null ? card.getCustomBankName() : bank == null ? "银行配置不可用" : bank.name());
            vo.setBankCode(bank == null ? "" : bank.code());
            vo.setTags(cardTags.getOrDefault(card.getId(), List.of()));
            vo.setCoverFileIds(covers.getOrDefault(card.getId(), List.of()));
            return vo;
        }).toList();
    }
    public BankCardVO detail(long userId, long id) {
        return list(userId).stream().filter(c -> c.getId().equals(Long.toString(id))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("银行卡不存在或无权访问"));
    }
    public String reveal(long userId, long id) {
        var card = repository.owned(userId, id);
        return crypto.decrypt(card.getCardNoCiphertext(), userId, id);
    }
    private void validateBank(Long bankId, BankCardEntity existing) {
        var typeIds = jdbc.queryForList("SELECT t.dict_id FROM sys_dict_type t JOIN sys_dict_data d ON d.dict_id=t.dict_id WHERE d.dict_code=? AND t.dict_type='bank' AND t.is_deleted=0 AND d.is_deleted=0", Long.class, bankId);
        if (typeIds.isEmpty()) throw new IllegalArgumentException("银行配置不存在");
        guard.lockType(typeIds.getFirst());
        // 锁获取后重新读，禁止与管理员删除/停用操作竞争。
        var options = jdbc.query("SELECT d.status,t.status AS type_status FROM sys_dict_data d JOIN sys_dict_type t ON t.dict_id=d.dict_id WHERE d.dict_code=? AND t.dict_type='bank' AND d.is_deleted=0 AND t.is_deleted=0 FOR UPDATE",
                (rs,n) -> "0".equals(rs.getString("status")) && "0".equals(rs.getString("type_status")), bankId);
        if (options.isEmpty()) throw new IllegalArgumentException("银行配置不存在");
        if (!options.getFirst() && (existing == null || !Objects.equals(existing.getBankId(), bankId)))
            throw new IllegalArgumentException("该银行已停用，请选择其他银行");
    }
    @Transactional(rollbackFor = Exception.class)
    public BankCardVO save(long userId, Long id, BankCardReq req) {
        guard.lockUser(userId);
        BankCardEntity existing = id == null ? null : repository.owned(userId, id);
        String customBankName = req.getCustomBankName() == null ? null : req.getCustomBankName().strip();
        if (customBankName != null && customBankName.isEmpty()) customBankName = null;
        if ((req.getBankId() == null) == (customBankName == null))
            throw new IllegalArgumentException("请选择银行或填写银行名称，不能同时提交两者");
        if (customBankName != null && customBankName.length() > 100)
            throw new IllegalArgumentException("银行名称最多100个字符");
        if (req.getBankId() != null) validateBank(req.getBankId(), existing);
        if ("debit".equals(req.getCardType()) && (req.getCreditLimit()!=null || req.getStatementDay()!=null || req.getRepaymentDay()!=null))
            throw new IllegalArgumentException("储蓄卡不能填写信用额度、账单日和还款日");
        if (req.getOpenedDate()!=null && req.getExpiryMonth()!=null && req.getExpiryMonth().withDayOfMonth(req.getExpiryMonth().lengthOfMonth()).isBefore(req.getOpenedDate()))
            throw new IllegalArgumentException("有效期不能早于开卡日期");
        if (req.getCoverSourceUrl()!=null && !req.getCoverSourceUrl().isBlank()) {
            try {
                URI uri = URI.create(req.getCoverSourceUrl());
                if (!Set.of("https","http").contains(uri.getScheme()) || uri.getHost()==null) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) { throw new IllegalArgumentException("卡面出处须为完整网页地址"); }
        }
        var card = new BankCardEntity();
        BeanUtils.copyProperties(req, card);
        card.setCustomBankName(customBankName);
        card.setId(id == null ? IdWorker.getId() : id);
        card.setUserId(userId);
        card.setExpiryMonth(req.getExpiryMonth()==null ? null : req.getExpiryMonth().withDayOfMonth(1));
        card.setSortOrder(req.getSortOrder()==null ? 0 : req.getSortOrder());
        if (existing == null) card.fillCreateCommonField(userId);
        else {
            card.setCreateTime(existing.getCreateTime()); card.setCreateUser(existing.getCreateUser());
            card.setIsDeleted(0); card.fillUpdateCommonField(userId);
        }
        if (req.getCardNo()!=null && !req.getCardNo().isBlank()) {
            String number = crypto.normalize(req.getCardNo());
            card.setCardNoFingerprint(crypto.fingerprint(number,userId));
            if (repository.duplicate(userId,card.getId(),card.getCardNoFingerprint())) throw new IllegalArgumentException("该银行卡已添加");
            card.setCardNoCiphertext(crypto.encrypt(number,userId,card.getId()));
            card.setCardNoLast4(number.substring(number.length()-4));
        } else if (existing != null) {
            card.setCardNoCiphertext(existing.getCardNoCiphertext()); card.setCardNoFingerprint(existing.getCardNoFingerprint());
            card.setCardNoLast4(existing.getCardNoLast4());
        } else throw new IllegalArgumentException("请输入卡号");
        Set<Long> tagIds = new LinkedHashSet<>(req.getTagIds());
        Set<Long> oldTags = new HashSet<>(jdbc.queryForList("SELECT tag_id FROM bank_card_tag_rel WHERE bank_card_id=? AND user_id=? AND is_deleted=0",Long.class,card.getId(),userId));
        Map<Long,BankCardVO.Tag> allowedTags = new HashMap<>();
        tags(userId).forEach(t -> allowedTags.put(Long.valueOf(t.id()),t));
        for (Long tagId : tagIds) {
            var tag = allowedTags.get(tagId);
            if (tag==null || (!"0".equals(tag.status()) && !oldTags.contains(tagId))) throw new IllegalArgumentException("标签不存在、已停用或不属于当前用户");
        }
        if (req.getCoverFileIds().isEmpty()) card.setCoverSourceUrl(null);
        repository.save(card,existing==null);
        jdbc.update("UPDATE bank_card_tag_rel SET is_deleted=1,update_user=?,update_time=CURRENT_TIMESTAMP WHERE bank_card_id=? AND user_id=? AND is_deleted=0",userId,card.getId(),userId);
        for (Long tagId : tagIds) jdbc.update("INSERT INTO bank_card_tag_rel(id,user_id,bank_card_id,tag_id,is_deleted,create_user,update_user,create_time,update_time) VALUES(?,?,?,?,0,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",IdWorker.getId(),userId,card.getId(),tagId,userId,userId);
        replaceCover(userId,card.getId(),req.getCoverFileIds());
        return detail(userId,card.getId());
    }
    private void replaceCover(long userId,long cardId,List<String> ids) {
        String fileId = ids.isEmpty() ? null : ids.getFirst();
        if (fileId != null) {
            var rows = jdbc.queryForList("SELECT biz_id FROM file WHERE id=? AND biz_type=? AND create_user=? AND is_public=0 AND is_deleted=0 FOR UPDATE",fileId,COVER_TYPE,userId);
            if (rows.size()!=1 || (rows.getFirst().get("biz_id")!=null && ((Number)rows.getFirst().get("biz_id")).longValue()!=cardId))
                throw new IllegalArgumentException("卡面文件不存在、已被使用或无权绑定");
        }
        jdbc.update("UPDATE file SET is_deleted=1,update_user=?,update_time=CURRENT_TIMESTAMP WHERE biz_type=? AND biz_id=? AND create_user=? AND is_deleted=0 AND (? IS NULL OR id<>?)",userId,COVER_TYPE,cardId,userId,fileId,fileId);
        if (fileId!=null) jdbc.update("UPDATE file SET biz_id=?,update_user=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND create_user=? AND is_deleted=0",cardId,userId,fileId,userId);
    }
    @Transactional(rollbackFor = Exception.class)
    public void delete(long userId,long id) {
        guard.lockUser(userId); repository.owned(userId,id);
        jdbc.update("UPDATE bank_card SET is_deleted=1,update_user=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND is_deleted=0",userId,id,userId);
        jdbc.update("UPDATE bank_card_tag_rel SET is_deleted=1,update_user=?,update_time=CURRENT_TIMESTAMP WHERE bank_card_id=? AND user_id=? AND is_deleted=0",userId,id,userId);
        replaceCover(userId,id,List.of());
    }
    @Transactional(rollbackFor = Exception.class)
    public BankCardVO.Tag saveTag(long userId,Long id,BankCardTagReq req) {
        guard.lockUser(userId);
        String name = req.name().strip();
        if(name.isEmpty()) throw new IllegalArgumentException("请输入标签名称");
        if(id!=null && tags(userId).stream().noneMatch(t->t.id().equals(id.toString()))) throw new IllegalArgumentException("标签不存在或无权修改");
        long duplicates = jdbc.queryForObject("SELECT COUNT(*) FROM user_dict_data WHERE user_id=? AND dict_type=? AND is_deleted=0 AND dict_label=? AND id<>?",Long.class,userId,TAG_TYPE,name,id==null?0L:id);
        if(duplicates>0) throw new IllegalArgumentException("标签名称已存在");
        long tagId = id==null?IdWorker.getId():id;
        if(id==null) jdbc.update("INSERT INTO user_dict_data(id,user_id,dict_type,dict_label,dict_value,color,dict_sort,status,is_default,is_readonly,is_deleted,create_user,update_user,create_time,update_time) VALUES(?,?,?,?,?,?,0,?,'N','N',0,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",tagId,userId,TAG_TYPE,name,Long.toString(tagId),req.color(),req.status(),userId,userId);
        else jdbc.update("UPDATE user_dict_data SET dict_label=?,color=?,status=?,update_user=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND dict_type=? AND is_deleted=0",name,req.color(),req.status(),userId,tagId,userId,TAG_TYPE);
        return new BankCardVO.Tag(Long.toString(tagId),name,req.color(),req.status());
    }
    @Transactional(rollbackFor = Exception.class)
    public void deleteTag(long userId,long id) {
        guard.lockUser(userId);
        if(tags(userId).stream().noneMatch(t->t.id().equals(Long.toString(id)))) throw new IllegalArgumentException("标签不存在或无权删除");
        jdbc.update("UPDATE user_dict_data SET is_deleted=1,update_user=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND dict_type=?",userId,id,userId,TAG_TYPE);
        jdbc.update("UPDATE bank_card_tag_rel SET is_deleted=1,update_user=?,update_time=CURRENT_TIMESTAMP WHERE tag_id=? AND user_id=? AND is_deleted=0",userId,id,userId);
    }
}
