package top.aiolife.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.system.mapper.StorageFileReferenceMapper;
import top.aiolife.system.mapper.StorageObjectMapper;
import top.aiolife.system.pojo.dto.StorageFileReference;

/** 删除前检查附件原始记录，包括尚未绑定业务以及逻辑删除的记录。 */
@Component
@RequiredArgsConstructor
public class StorageFileReferenceGuard {
    private final StorageFileReferenceMapper mapper;
    private final StorageObjectMapper storageObjects;

    public void check(String bucket, String key) {
        if (storageObjects.countStoredObject(bucket, key) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "不允许删除：图片对象由共享文件引用管理");
        var reference = mapper.selectReferencesIncludingDeleted(key).stream().findFirst();
        if (reference.isPresent()) {
            var row = reference.get();
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "不允许删除：attachment 表存在关联记录（ID：" + row.id()
                            + "，业务类型：" + (StringUtils.hasText(row.bizType()) ? row.bizType() : "未指定")
                            + "，业务 ID：" + (row.bizId() == null ? "未绑定" : row.bizId())
                            + (row.deleted() == 1 ? "，已软删除" : "") + "）");
        }
    }

}
