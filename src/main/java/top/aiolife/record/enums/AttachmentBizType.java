package top.aiolife.record.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 文件业务类型。
 */
@Getter
public enum AttachmentBizType {

    BANK_CARD_TEMPLATE_COVER("bank_card_template_face", "system/bank-card-covers", AttachmentVisibility.PRIVATE),
    BANK_CARD_COVER("bank_card_face", "bank-card", AttachmentVisibility.PRIVATE),
    AVATAR("avatar", "avatar", AttachmentVisibility.PUBLIC),
    WARDROBE_ITEM("wardrobe_item_cover", "wardrobe", AttachmentVisibility.PRIVATE),
    FEEDBACK("feedback", "feedback", AttachmentVisibility.PRIVATE),
    FEEDBACK_COMMENT("feedback_comment", "feedback", AttachmentVisibility.PRIVATE),
    MOVIE("movie_cover", "movie", AttachmentVisibility.PRIVATE),
    HONOR_RECORD("honor_record", "honor", AttachmentVisibility.PRIVATE),
    READ_RECORD("read_cover", "read", AttachmentVisibility.PRIVATE),
    EVENT_RECORD("event_record", "event-record", AttachmentVisibility.PRIVATE),
    DEVICE("device_cover", "device", AttachmentVisibility.PRIVATE),
    CBTI_PERSONALITY("cbti_personality_image", "cbti", AttachmentVisibility.PUBLIC);

    private final String bizType;
    private final String directory;
    private final AttachmentVisibility visibility;

    AttachmentBizType(String bizType, String directory, AttachmentVisibility visibility) {
        this.bizType = bizType;
        this.directory = directory;
        this.visibility = visibility;
    }

    /**
     * 根据接口传入的业务类型获取枚举。
     */
    public static AttachmentBizType fromBizType(String bizType) {
        return Arrays.stream(values())
                .filter(item -> item.bizType.equals(bizType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的文件业务类型: " + bizType));
    }
}
