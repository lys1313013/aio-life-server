package top.aiolife.bankcard.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.net.URI;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.bankcard.mapper.*;
import top.aiolife.bankcard.pojo.entity.BankCardTagRelEntity;
import top.aiolife.bankcard.pojo.dto.BankCardTemplateCover;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.bankcard.pojo.entity.BankCardEntity;
import top.aiolife.bankcard.pojo.req.BankCardReq;
import top.aiolife.bankcard.pojo.req.BankCardTagReq;
import top.aiolife.bankcard.pojo.vo.BankCardVO;

@Service
@RequiredArgsConstructor
public class BankCardService {
    public static final String TAG_TYPE = "bank_card_tag";
    public static final String COVER_TYPE = "bank_card_cover";
    private final BankCardMapper cardMapper;
    private final BankCardCrypto crypto;
    private final BankCardDictionaryGuard guard;
    private final BankCardDictionaryMapper dictionaryMapper;
    private final BankCardTagMapper tagMapper;
    private final BankCardTagRelMapper tagRelMapper;
    private final BankCardFileMapper fileMapper;
    private final BankCardCoverTemplateMapper templateMapper;
    private final BankCardCoverTemplateService templates;

    public List<BankCardVO.Bank> banks() {
        return dictionaryMapper.selectBanks();
    }
    public List<BankCardVO.Tag> tags(long userId) {
        return tagMapper.selectTags(userId);
    }
    public List<BankCardVO> list(long userId) {
        var bankMap = new HashMap<String, BankCardVO.Bank>();
        banks().forEach(b -> bankMap.put(b.id(), b));
        var tagMap = new HashMap<String, BankCardVO.Tag>();
        tags(userId).forEach(t -> tagMap.put(t.id(), t));
        Map<Long, List<BankCardVO.Tag>> cardTags = new HashMap<>();
        for (var relation : tagRelMapper.listByUser(userId)) {
            var tag = tagMap.get(relation.getTagId().toString());
            if (tag != null) cardTags.computeIfAbsent(relation.getBankCardId(), k -> new ArrayList<>()).add(tag);
        }
        Map<Long, List<String>> covers = new HashMap<>();
        for (var file : fileMapper.selectPrivateCovers(userId)) {
            covers.computeIfAbsent(file.bizId(), k -> new ArrayList<>()).add(file.id());
        }
        Map<Long, BankCardTemplateCover> templateCovers = new HashMap<>();
        templateMapper.selectUsedCovers(userId).forEach(cover -> templateCovers.put(cover.id(), cover));
        return cardMapper.list(userId).stream().map(card -> {
            var vo = new BankCardVO();
            BeanUtils.copyProperties(card, vo);
            vo.setId(card.getId().toString());
            if (card.getCardNoCiphertext() != null)
                vo.setCardNoFirst4(crypto.decrypt(card.getCardNoCiphertext(), userId, card.getId()).substring(0, 4));
            vo.setBankId(card.getBankId() == null ? null : card.getBankId().toString());
            var bank = bankMap.get(vo.getBankId());
            vo.setBankName(card.getBankId() == null ? card.getCustomBankName() : bank == null ? "银行配置不可用" : bank.name());
            vo.setBankCode(bank == null ? "" : bank.code());
            vo.setTags(cardTags.getOrDefault(card.getId(), List.of()));
            vo.setCoverFileIds(covers.getOrDefault(card.getId(), List.of()));
            vo.setCoverTemplateId(card.getCoverTemplateId()==null ? null : card.getCoverTemplateId().toString());
            var template=templateCovers.get(card.getCoverTemplateId());
            if (template!=null) {
                vo.setCoverTemplateFileId(template.fileId());
                vo.setCoverTemplatePublicUrl(templates.publicUrl(template.fileId()));
                vo.setCoverTemplateName(template.name());
                vo.setCoverSourceUrl(template.sourceUrl());
            }
            return vo;
        }).toList();
    }
    public BankCardVO detail(long userId, long id) {
        return list(userId).stream().filter(c -> c.getId().equals(Long.toString(id))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("银行卡不存在或无权访问"));
    }
    public String reveal(long userId, long id) {
        var card = cardMapper.owned(userId, id);
        if (card.getCardNoCiphertext() == null) throw new IllegalArgumentException("尚未填写卡号");
        return crypto.decrypt(card.getCardNoCiphertext(), userId, id);
    }
    private void validateBank(Long bankId, BankCardEntity existing) {
        Long typeId = dictionaryMapper.selectBankTypeId(bankId);
        if (typeId == null) throw new IllegalArgumentException("银行配置不存在");
        guard.lockType(typeId);
        // 锁获取后重新读，禁止与管理员删除/停用操作竞争。
        Boolean enabled = dictionaryMapper.lockBankEnabled(bankId);
        if (enabled == null) throw new IllegalArgumentException("银行配置不存在");
        if (!enabled && (existing == null || !Objects.equals(existing.getBankId(), bankId)))
            throw new IllegalArgumentException("该银行已停用，请选择其他银行");
    }
    @Transactional(rollbackFor = Exception.class)
    public BankCardVO save(long userId, Long id, BankCardReq req) {
        guard.lockUser(userId);
        BankCardEntity existing = id == null ? null : cardMapper.owned(userId, id);
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
        if (req.getCoverTemplateId()!=null && !req.getCoverFileIds().isEmpty())
            throw new IllegalArgumentException("公共卡面与私人上传不能同时选择");
        templates.validateSelection(existing==null ? null : existing.getCoverTemplateId(),req.getCoverTemplateId(),req.getBankId(),req.getCardType());
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
            if (cardMapper.duplicate(userId,card.getId(),card.getCardNoFingerprint())) throw new IllegalArgumentException("该银行卡已添加");
            card.setCardNoCiphertext(crypto.encrypt(number,userId,card.getId()));
            card.setCardNoLast4(number.substring(number.length()-4));
        } else if (existing != null) {
            card.setCardNoCiphertext(existing.getCardNoCiphertext()); card.setCardNoFingerprint(existing.getCardNoFingerprint());
            card.setCardNoLast4(existing.getCardNoLast4());
        }
        Set<Long> tagIds = new LinkedHashSet<>(req.getTagIds());
        Set<Long> oldTags = new HashSet<>(tagRelMapper.selectTagIds(card.getId(), userId));
        Map<Long,BankCardVO.Tag> allowedTags = new HashMap<>();
        tags(userId).forEach(t -> allowedTags.put(Long.valueOf(t.id()),t));
        for (Long tagId : tagIds) {
            var tag = allowedTags.get(tagId);
            if (tag==null || (!"0".equals(tag.status()) && !oldTags.contains(tagId))) throw new IllegalArgumentException("标签不存在、已停用或不属于当前用户");
        }
        if (req.getCoverFileIds().isEmpty()) card.setCoverSourceUrl(null);
        cardMapper.save(card,existing==null);
        tagRelMapper.deleteByCard(userId, card.getId());
        for (Long tagId : tagIds) {
            var relation = new BankCardTagRelEntity();
            relation.setUserId(userId);
            relation.setBankCardId(card.getId());
            relation.setTagId(tagId);
            relation.fillCreateCommonField(userId);
            tagRelMapper.insert(relation);
        }
        replaceCover(userId,card.getId(),req.getCoverFileIds());
        return detail(userId,card.getId());
    }
    private void replaceCover(long userId,long cardId,List<String> ids) {
        String fileId = ids.isEmpty() ? null : ids.getFirst();
        if (fileId != null) {
            var file = fileMapper.lockPrivateCover(fileId, userId);
            if (file == null || (file.bizId() != null && file.bizId() != cardId))
                throw new IllegalArgumentException("卡面文件不存在、已被使用或无权绑定");
        }
        fileMapper.deletePrivateCoversExcept(userId, cardId, fileId);
        if (fileId != null) fileMapper.bindPrivateCover(cardId, userId, fileId);
    }
    @Transactional(rollbackFor = Exception.class)
    public void delete(long userId,long id) {
        guard.lockUser(userId);
        var existing=cardMapper.owned(userId,id);
        templates.validateSelection(existing.getCoverTemplateId(),null,null,null);
        cardMapper.softDeleteOwned(userId, id);
        tagRelMapper.deleteByCard(userId, id);
        replaceCover(userId,id,List.of());
    }
    @Transactional(rollbackFor = Exception.class)
    public BankCardVO.Tag saveTag(long userId,Long id,BankCardTagReq req) {
        guard.lockUser(userId);
        String name = req.name().strip();
        if(name.isEmpty()) throw new IllegalArgumentException("请输入标签名称");
        if(id!=null && tags(userId).stream().noneMatch(t->t.id().equals(id.toString()))) throw new IllegalArgumentException("标签不存在或无权修改");
        long duplicates = tagMapper.countDuplicate(userId, name, id == null ? 0L : id);
        if(duplicates>0) throw new IllegalArgumentException("标签名称已存在");
        long tagId = id==null?IdWorker.getId():id;
        if (id == null) {
            var tag = new UserDictDataEntity();
            tag.setId(tagId);
            tag.setUserId(userId);
            tag.setDictType(TAG_TYPE);
            tag.setDictLabel(name);
            tag.setDictValue(Long.toString(tagId));
            tag.setColor(req.color());
            tag.setDictSort(0);
            tag.setStatus(req.status());
            tag.setIsDefault("N");
            tag.setIsReadonly("N");
            tag.fillCreateCommonField(userId);
            tagMapper.insert(tag);
        } else {
            tagMapper.updateTag(userId, tagId, name, req.color(), req.status());
        }
        return new BankCardVO.Tag(Long.toString(tagId),name,req.color(),req.status());
    }
    @Transactional(rollbackFor = Exception.class)
    public void deleteTag(long userId,long id) {
        guard.lockUser(userId);
        if(tags(userId).stream().noneMatch(t->t.id().equals(Long.toString(id)))) throw new IllegalArgumentException("标签不存在或无权删除");
        tagMapper.deleteTag(userId, id);
        tagRelMapper.deleteByTag(userId, id);
    }
}
