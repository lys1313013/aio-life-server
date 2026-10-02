package top.aiolife.system.pojo.vo;

/** MinIO 的实时对象信息，不依赖业务 file 表。 */
public record StorageObjectVO(String key, boolean directory,
                              long size,
                              String lastModified, boolean previewable) {
}
