package top.aiolife.core.id;

import java.util.UUID;

/** 附件对外标识使用安全随机 UUIDv4，与内部雪花主键分离。 */
public final class AttachmentKey {
    public static final String PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";

    private AttachmentKey() {}

    public static String next() {
        return UUID.randomUUID().toString();
    }

    public static boolean isValid(String id) {
        return id != null && id.matches(PATTERN);
    }

    public static void require(String id) {
        if (!isValid(id)) throw new IllegalArgumentException("附件标识必须为 UUIDv4");
    }
}
