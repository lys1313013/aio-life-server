package top.aiolife.bankcard.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.net.URI;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.bankcard.pojo.entity.BankCardCoverTemplateEntity;
import top.aiolife.bankcard.pojo.req.BankCardCoverTemplateReq;
import top.aiolife.bankcard.pojo.vo.BankCardCoverTemplateVO;

/** 公共模板及文件绑定；锁顺序为银行字典类型、模板、文件。 */
@Service
@RequiredArgsConstructor
public class BankCardCoverTemplateService {
    public static final String FILE_TYPE = "bank_card_template_cover";
    private final JdbcTemplate jdbc;
    private final BankCardDictionaryGuard guard;

    private static final String SELECT = """
        SELECT t.*,d.dict_label AS bank_name,f.id AS file_id,
          (SELECT COUNT(*) FROM bank_card c WHERE c.cover_template_id=t.id AND c.is_deleted=0) AS usage_count
        FROM bank_card_cover_template t
        LEFT JOIN sys_dict_data d ON d.dict_code=t.bank_id
        LEFT JOIN file f ON f.biz_id=t.id AND f.biz_type='bank_card_template_cover' AND f.is_deleted=0
        WHERE t.is_deleted=0
        """;

    public List<BankCardCoverTemplateVO> list() {
        return query(" ORDER BY t.sort_order,t.id", new Object[0]);
    }
    private List<BankCardCoverTemplateVO> query(String suffix, Object... args) {
        return jdbc.query(SELECT + suffix, (rs,n) -> new BankCardCoverTemplateVO(
                rs.getString("id"),rs.getString("name"),rs.getString("bank_id"),rs.getString("bank_name"),
                rs.getString("card_type"),rs.getString("source_url"),rs.getInt("is_enabled"),rs.getInt("sort_order"),
                rs.getString("file_id"),rs.getLong("usage_count")), args);
    }
    public BankCardCoverTemplateVO detail(long id) {
        return query(" AND t.id=?",id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("公共卡面不存在"));
    }
    public List<BankCardCoverTemplateVO.Option> options(long bankId, String cardType) {
        return jdbc.query("""
            SELECT t.id,t.name,f.id AS file_id FROM bank_card_cover_template t
            JOIN file f ON f.biz_id=t.id AND f.biz_type='bank_card_template_cover' AND f.is_deleted=0
            JOIN sys_dict_data d ON d.dict_code=t.bank_id AND d.is_deleted=0 AND d.status='0'
            JOIN sys_dict_type dt ON dt.dict_id=d.dict_id AND dt.dict_type='bank' AND dt.is_deleted=0 AND dt.status='0'
            WHERE t.bank_id=? AND t.card_type=? AND t.is_deleted=0 AND t.is_enabled=1
            ORDER BY t.sort_order,t.id
            """, (rs,n) -> new BankCardCoverTemplateVO.Option(rs.getString("id"),rs.getString("name"),rs.getString("file_id")), bankId,cardType);
    }
    private BankCardCoverTemplateEntity lock(long id) {
        return jdbc.query("SELECT * FROM bank_card_cover_template WHERE id=? AND is_deleted=0 FOR UPDATE",
                BeanPropertyRowMapper.newInstance(BankCardCoverTemplateEntity.class),id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("公共卡面不存在"));
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
            if (jdbc.queryForObject("SELECT COUNT(*) FROM file WHERE biz_type=? AND biz_id=? AND is_deleted=0",Long.class,FILE_TYPE,id)!=1)
                throw new IllegalArgumentException("公共卡面图片不可用");
        }
    }
    private boolean bankEnabled(long id) {
        return jdbc.queryForObject("""
            SELECT COUNT(*) FROM sys_dict_data d JOIN sys_dict_type t ON t.dict_id=d.dict_id
            WHERE d.dict_code=? AND t.dict_type='bank' AND d.is_deleted=0 AND t.is_deleted=0 AND d.status='0' AND t.status='0'
            """,Long.class,id)>0;
    }
    private void lockBanks() {
        jdbc.queryForList("SELECT dict_id FROM sys_dict_type WHERE dict_type='bank' AND is_deleted=0 ORDER BY dict_id",Long.class)
                .forEach(guard::lockType);
    }
    private List<Long> references(long id) {
        // 当前读，避免 MySQL REPEATABLE READ 的历史快照漏掉刚提交的引用。
        return jdbc.queryForList("SELECT id FROM bank_card WHERE cover_template_id=? AND is_deleted=0 FOR UPDATE",Long.class,id);
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
        var files=jdbc.queryForList("SELECT biz_id,create_user FROM file WHERE id=? AND biz_type=? AND is_deleted=0 AND is_public=0 FOR UPDATE",req.getFileId(),FILE_TYPE);
        if (files.size()!=1) throw new IllegalArgumentException("请选择有效的公共卡面图片");
        var file=files.getFirst();
        Long bound=file.get("biz_id")==null ? null : ((Number)file.get("biz_id")).longValue();
        if (bound==null ? !Objects.equals(((Number)file.get("create_user")).longValue(),userId) : bound!=templateId)
            throw new IllegalArgumentException("图片已被使用或不属于本次上传");
        if (old==null) {
            jdbc.update("INSERT INTO bank_card_cover_template(id,name,bank_id,card_type,source_url,is_enabled,sort_order,create_user,update_user) VALUES(?,?,?,?,?,?,?,?,?)",
                    templateId,req.getName().strip(),req.getBankId(),req.getCardType(),source,req.getIsEnabled(),req.getSortOrder(),userId,userId);
        } else {
            jdbc.update("UPDATE bank_card_cover_template SET name=?,bank_id=?,card_type=?,source_url=?,is_enabled=?,sort_order=?,update_user=?,update_time=CURRENT_TIMESTAMP WHERE id=?",
                    req.getName().strip(),req.getBankId(),req.getCardType(),source,req.getIsEnabled(),req.getSortOrder(),userId,templateId);
        }
        jdbc.update("UPDATE file SET is_deleted=1,update_user=?,update_time=CURRENT_TIMESTAMP WHERE biz_type=? AND biz_id=? AND id<>? AND is_deleted=0",userId,FILE_TYPE,templateId,req.getFileId());
        jdbc.update("UPDATE file SET biz_id=?,update_user=?,update_time=CURRENT_TIMESTAMP WHERE id=?",templateId,userId,req.getFileId());
        return detail(templateId);
    }
    @Transactional(rollbackFor=Exception.class)
    public BankCardCoverTemplateVO setEnabled(long userId, long id, int enabled) {
        lockBanks();
        var template=lock(id);
        if (enabled==1 && !bankEnabled(template.getBankId())) throw new IllegalArgumentException("所属银行已停用");
        jdbc.update("UPDATE bank_card_cover_template SET is_enabled=?,update_user=?,update_time=CURRENT_TIMESTAMP WHERE id=?",enabled,userId,id);
        return detail(id);
    }
    @Transactional(rollbackFor=Exception.class)
    public void delete(long userId,long id) {
        lock(id);
        if (!references(id).isEmpty()) throw new IllegalArgumentException("卡面已被使用，请停用而非删除");
        jdbc.update("UPDATE bank_card_cover_template SET is_deleted=1,update_user=?,update_time=CURRENT_TIMESTAMP WHERE id=?",userId,id);
        jdbc.update("UPDATE file SET is_deleted=1,update_user=?,update_time=CURRENT_TIMESTAMP WHERE biz_type=? AND biz_id=? AND is_deleted=0",userId,FILE_TYPE,id);
    }
}
