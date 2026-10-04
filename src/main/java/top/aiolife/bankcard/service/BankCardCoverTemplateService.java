package top.aiolife.bankcard.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.net.URI;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import top.aiolife.bankcard.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.bankcard.pojo.entity.BankCardCoverTemplateEntity;
import top.aiolife.bankcard.pojo.req.BankCardCoverTemplateReq;
import top.aiolife.bankcard.pojo.vo.BankCardCoverTemplateVO;
import top.aiolife.bankcard.pojo.query.BankCardCoverQuery;
import top.aiolife.core.resq.PageResp;

/** 公共模板及文件绑定；锁顺序为银行字典类型、模板、文件。 */
@Service
@RequiredArgsConstructor
public class BankCardCoverTemplateService {
    public static final String FILE_TYPE = "bank_card_template_cover";
    private final BankCardCoverTemplateMapper mapper;
    private final BankCardDictionaryGuard guard;
    private final BankCardDictionaryMapper dictionaryMapper;
    private final BankCardMapper cardMapper;
    private final BankCardFileMapper fileMapper;

    private final top.aiolife.core.util.MinioUtil minio;

    public String publicUrl(String fileId) { return minio.getPublicImageUrl(fileId, "image/png"); }
    private BankCardCoverTemplateVO withUrl(BankCardCoverTemplateVO cover) {
        return cover.withPublicUrl(publicUrl(cover.fileId()));
    }

    public List<BankCardCoverTemplateVO> list() {
        return mapper.selectDetails().stream().map(this::withUrl).toList();
    }
    public PageResp<BankCardCoverTemplateVO> page(BankCardCoverQuery query) {
        if (query.getKeyword() != null) query.setKeyword(query.getKeyword().strip());
        return PageResp.of(mapper.selectPage(query, ((long) query.getPage() - 1) * query.getSize()).stream().map(this::withUrl).toList(), mapper.countPage(query));
    }
    public BankCardCoverTemplateVO detail(long id) {
        var detail = mapper.selectDetail(id);
        if (detail == null) throw new IllegalArgumentException("公共卡面不存在");
        return withUrl(detail);
    }
    public List<BankCardCoverTemplateVO.Option> options(long bankId, String cardType) {
        return mapper.selectOptions(bankId, cardType).stream()
                .map(c -> new BankCardCoverTemplateVO.Option(c.id(), c.name(), c.fileId(), publicUrl(c.fileId()))).toList();
    }
    private BankCardCoverTemplateEntity lock(long id) {
        var template = mapper.lockById(id);
        if (template == null) throw new IllegalArgumentException("公共卡面不存在");
        return template;
    }
    /** 卡片解除或改绑也拿模板锁；按 ID 排序避免交叉换卡面死锁。 */
    public void validateSelection(Long previousId, Long nextId, Long bankId, String cardType) {
        var ids = new TreeSet<Long>();
        if (previousId != null) ids.add(previousId);
        if (nextId != null) ids.add(nextId);
        for (long id : ids) {
            var template = lock(id);
            if (!Objects.equals(id,nextId)) continue;
            if (!Objects.equals(bankId,template.getBankId()) || !Objects.equals(cardType,template.getCardType()))
                throw new IllegalArgumentException("公共卡面与银行或银行卡类型不匹配");
            if (!Objects.equals(previousId,nextId)) {
                if (template.getIsEnabled()!=1 || !bankEnabled(bankId))
                    throw new IllegalArgumentException("公共卡面或所属银行已停用");
            }
            if (fileMapper.countTemplateFiles(id)!=1)
                throw new IllegalArgumentException("公共卡面图片不可用");
        }
    }
    private boolean bankEnabled(long id) {
        return dictionaryMapper.countEnabledBank(id) > 0;
    }
    private void lockBanks() {
        dictionaryMapper.selectBankTypeIds().forEach(guard::lockType);
    }
    private List<Long> references(long id) {
        // 当前读，避免 MySQL REPEATABLE READ 的历史快照漏掉刚提交的引用。
        return cardMapper.lockTemplateReferences(id);
    }
    @Transactional(rollbackFor=Exception.class)
    public BankCardCoverTemplateVO save(long userId, Long id, BankCardCoverTemplateReq req) {
        lockBanks();
        var old = id==null ? null : lock(id);
        long templateId = id==null ? IdWorker.getId() : id;
        boolean changed = old==null || !Objects.equals(old.getBankId(),req.getBankId()) || !Objects.equals(old.getCardType(),req.getCardType());
        if (old!=null && changed && !references(id).isEmpty())
            throw new IllegalArgumentException("卡面已被使用，不能修改银行或类型，请新建卡面");
        if (!bankEnabled(req.getBankId()) && (changed || (req.getIsEnabled()==1 && old.getIsEnabled()==0)))
            throw new IllegalArgumentException("请选择已启用的系统银行");
        String source = req.getSourceUrl()==null || req.getSourceUrl().isBlank() ? null : req.getSourceUrl().strip();
        if (source!=null) {
            try {
                var uri=URI.create(source);
                if (!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) { throw new IllegalArgumentException("卡面出处须为完整网页地址"); }
        }
        var file = fileMapper.lockTemplateCover(req.getFileId());
        if (file == null) throw new IllegalArgumentException("请选择有效的公共卡面图片");
        Long bound = file.bizId();
        if (bound == null ? !Objects.equals(file.createUser(), userId) : bound != templateId)
            throw new IllegalArgumentException("图片已被使用或不属于本次上传");
        var template = new BankCardCoverTemplateEntity();
        BeanUtils.copyProperties(req, template);
        template.setId(templateId);
        template.setName(req.getName().strip());
        template.setSourceUrl(source);
        if (old == null) {
            template.fillCreateCommonField(userId);
            mapper.insert(template);
        } else {
            template.fillUpdateCommonField(userId);
            mapper.updateById(template);
        }
        fileMapper.deleteTemplateCoversExcept(userId, templateId, req.getFileId());
        fileMapper.bindTemplateCover(templateId, userId, req.getFileId());
        return detail(templateId);
    }
    @Transactional(rollbackFor=Exception.class)
    public BankCardCoverTemplateVO setEnabled(long userId, long id, int enabled) {
        lockBanks();
        var template=lock(id);
        if (enabled==1 && !bankEnabled(template.getBankId())) throw new IllegalArgumentException("所属银行已停用");
        mapper.setEnabled(userId, id, enabled);
        return detail(id);
    }
    @Transactional(rollbackFor=Exception.class)
    public void delete(long userId,long id) {
        lock(id);
        if (!references(id).isEmpty()) throw new IllegalArgumentException("卡面已被使用，请停用而非删除");
        mapper.softDelete(userId, id);
        fileMapper.deleteTemplateCoversExcept(userId, id, null);
    }
}
