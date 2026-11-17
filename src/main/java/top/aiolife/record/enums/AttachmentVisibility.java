package top.aiolife.record.enums;

import lombok.Getter;

/**
 * 文件可见性。
 */
@Getter
public enum AttachmentVisibility {

    PRIVATE(0),
    PUBLIC(1);

    private final int value;

    AttachmentVisibility(int value) {
        this.value = value;
    }
}
