package top.aiolife.system.pojo.vo;

import java.util.List;

/** 当前配置桶的一页目录或对象，不计算全桶数量。 */
public record StoragePageVO(String bucket, String prefix, List<StorageObjectVO> items,
                            String nextCursor) {
}
