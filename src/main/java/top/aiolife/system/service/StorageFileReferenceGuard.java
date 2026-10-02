package top.aiolife.system.service;

import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.record.enums.FileBizType;

/** 删除前检查 file 原始记录，包括尚未绑定业务以及逻辑删除的记录。 */
@Component
@RequiredArgsConstructor
public class StorageFileReferenceGuard {
    private final JdbcTemplate jdbc;

    public void check(String bucket, String key) {
        String basename = key.substring(key.lastIndexOf('/') + 1);
        // 不通过 BaseMapper 查询，避免 @TableLogic 自动隐藏仍需保护的历史关联。
        // LIKE 显式转义，MinIO 对象名中的 %、_、! 均作为普通字符处理。
        var candidates = jdbc.query("""
                SELECT id, file_name, create_user, biz_type, biz_id, is_deleted
                FROM file
                WHERE file_name IN (?, ?, ?, ?)
                   OR file_name LIKE ? ESCAPE '!' OR file_name LIKE ? ESCAPE '!'
                """, (rs, row) -> new Reference(rs.getString("id"), rs.getString("file_name"),
                rs.getString("create_user"), rs.getString("biz_type"), rs.getString("biz_id"),
                rs.getInt("is_deleted")), key, basename, bucket + "/" + key, bucket + "/" + basename,
                "%" + escapeLike("/" + bucket + "/" + key), "%" + escapeLike("/" + bucket + "/" + basename));
        var reference = candidates.stream().filter(row -> matches(row, bucket, key)).findFirst();
        if (reference.isPresent()) {
            var row = reference.get();
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "不允许删除：file 表存在关联记录（ID：" + row.id()
                            + "，业务类型：" + (StringUtils.hasText(row.bizType()) ? row.bizType() : "未指定")
                            + "，业务 ID：" + (row.bizId() == null ? "未绑定" : row.bizId())
                            + (row.deleted() == 1 ? "，已软删除" : "") + "）");
        }
    }

    private boolean matches(Reference row, String bucket, String key) {
        String name = row.name();
        if (key.equals(name) || (bucket + "/" + key).equals(name)
                || name.endsWith("/" + bucket + "/" + key)) return true;
        int bucketIndex = name.indexOf("/" + bucket + "/");
        if (bucketIndex >= 0) name = name.substring(bucketIndex + bucket.length() + 2);
        else if (name.startsWith(bucket + "/")) name = name.substring(bucket.length() + 1);
        if (name.contains("/") || row.owner() == null || !StringUtils.hasText(row.bizType())) return false;
        // 对齐 SysFileController 的历史查找路径，并覆盖当前业务枚举中的目录别名。
        Set<String> directories = new LinkedHashSet<>();
        directories.add(row.bizType());
        directories.add(row.bizType().replace('_', '-'));
        if (row.bizType().endsWith("_record")) {
            directories.add(row.bizType().substring(0, row.bizType().length() - "_record".length()));
        }
        for (var type : FileBizType.values()) {
            if (type.getBizType().equals(row.bizType())) directories.add(type.getDirectory());
        }
        String filename = name;
        return directories.stream().anyMatch(dir -> key.equals(row.owner() + "/" + dir + "/" + filename));
    }

    private String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private record Reference(String id, String name, String owner, String bizType, String bizId, int deleted) {}
}
