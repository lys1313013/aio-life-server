package top.aiolife.bankcard.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import top.aiolife.bankcard.pojo.entity.BankCardEntity;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** 显式查询字段和归属条件，避免存储实体意外出现在响应中。 */
@Repository
@RequiredArgsConstructor
public class BankCardRepository {
    private final JdbcTemplate jdbc;
    private static final String FIELDS = "bank_id,custom_bank_name,card_name,alias,card_type,card_no_ciphertext,card_no_fingerprint,card_no_last4,branch_name,status,opened_date,expiry_month,credit_limit,statement_day,repayment_day,cover_color,cover_source_url,sort_order,remark,update_user,update_time";
    private static String property(String column) {
        String[] parts = column.split("_");
        return parts[0] + Arrays.stream(parts).skip(1)
                .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1)).collect(Collectors.joining());
    }
    public List<BankCardEntity> list(long userId) {
        return jdbc.query("SELECT * FROM bank_card WHERE user_id=? AND is_deleted=0 ORDER BY sort_order,id DESC",
                BeanPropertyRowMapper.newInstance(BankCardEntity.class), userId);
    }
    public BankCardEntity owned(long userId, long id) {
        var rows = jdbc.query("SELECT * FROM bank_card WHERE user_id=? AND id=? AND is_deleted=0",
                BeanPropertyRowMapper.newInstance(BankCardEntity.class), userId, id);
        if (rows.isEmpty()) throw new IllegalArgumentException("银行卡不存在或无权访问");
        return rows.getFirst();
    }
    public void save(BankCardEntity card, boolean insert) {
        String columns = "id,user_id,create_user,create_time,is_deleted," + FIELDS;
        String sql = insert
                ? "INSERT INTO bank_card (" + columns + ") VALUES (" + Arrays.stream(columns.split(",")).map(s -> ":" + property(s)).collect(Collectors.joining(",")) + ")"
                : "UPDATE bank_card SET " + Arrays.stream(FIELDS.split(",")).map(s -> s + "=:" + property(s)).collect(Collectors.joining(",")) + " WHERE id=:id AND user_id=:userId AND is_deleted=0";
        if (new NamedParameterJdbcTemplate(jdbc).update(sql, new BeanPropertySqlParameterSource(card)) != 1)
            throw new IllegalStateException("银行卡保存失败");
    }
    public boolean duplicate(long userId, long id, byte[] fingerprint) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM bank_card WHERE user_id=? AND card_no_fingerprint=? AND id<>? AND is_deleted=0",
                Long.class, userId, fingerprint, id) > 0;
    }
}
