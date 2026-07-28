package top.aiolife.record.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 文件业务类型。
 */
@Getter
public enum FileBizType {

    AVATAR("avatar", "avatar", FileVisibility.PUBLIC),
    WARDROBE_ITEM("wardrobe_item", "wardrobe", FileVisibility.PRIVATE),
    FEEDBACK("feedback", "feedback", FileVisibility.PRIVATE),
    FEEDBACK_COMMENT("feedback_comment", "feedback", FileVisibility.PRIVATE),
    MOVIE("movie", "movie", FileVisibility.PRIVATE),
    HONOR_RECORD("honor_record", "honor", FileVisibility.PRIVATE),
    READ_RECORD("read", "read", FileVisibility.PRIVATE),
    PERFORMANCE("performance", "performance", FileVisibility.PRIVATE),
    DEVICE("device", "device", FileVisibility.PRIVATE);

    private final String bizType;
    private final String directory;
    private final FileVisibility visibility;

    FileBizType(String bizType, String directory, FileVisibility visibility) {
        this.bizType = bizType;
        this.directory = directory;
        this.visibility = visibility;
    }

    /**
     * 根据接口传入的业务类型获取枚举。
     */
    public static FileBizType fromBizType(String bizType) {
        return Arrays.stream(values())
                .filter(item -> item.bizType.equals(bizType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的文件业务类型: " + bizType));
    }
}
