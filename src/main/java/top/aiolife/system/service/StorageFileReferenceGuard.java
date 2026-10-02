package top.aiolife.system.service;

import top.aiolife.config.CbtiConfig;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import top.aiolife.system.mapper.StorageFileReferenceMapper;
import top.aiolife.system.pojo.dto.StorageFileReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.record.enums.FileBizType;

/** 删除前检查 file 原始记录，包括尚未绑定业务以及逻辑删除的记录。 */
@Component
@RequiredArgsConstructor
public class StorageFileReferenceGuard {
    private final StorageFileReferenceMapper mapper;
    private final CbtiConfig cbtiConfig;

    public void check(String bucket, String key) {
        if ((!StringUtils.hasText(cbtiConfig.getBucketName()) || bucket.equals(cbtiConfig.getBucketName()))
                && mapper.countCbtiReferencesIncludingDeleted(key) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "不允许删除：CBTI 人格存在图片引用（含软删除记录）");
        String basename = key.substring(key.lastIndexOf('/') + 1);
        // 显式查询包含软删除记录；LIKE 中的 %、_、! 均按普通字符处理。
        var candidates = mapper.selectReferencesIncludingDeleted(key, basename, bucket + "/" + key,
                bucket + "/" + basename, "%" + escapeLike("/" + bucket + "/" + key),
                "%" + escapeLike("/" + bucket + "/" + basename));
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

    private boolean matches(StorageFileReference row, String bucket, String key) {
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

}
