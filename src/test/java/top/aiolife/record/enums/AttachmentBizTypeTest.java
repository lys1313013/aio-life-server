package top.aiolife.record.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentBizTypeTest {

    @Test
    void fromBizType_支持所有已定义业务类型() {
        for (AttachmentBizType type : AttachmentBizType.values()) {
            assertEquals(type, AttachmentBizType.fromBizType(type.getBizType()));
        }
    }

    @Test
    void fromBizType_拒绝未知业务类型() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AttachmentBizType.fromBizType("unknown")
        );

        assertEquals("不支持的文件业务类型: unknown", exception.getMessage());
    }

    @Test
    void visibility_头像和人格图片公开且其他文件私有() {
        assertEquals(AttachmentVisibility.PUBLIC, AttachmentBizType.AVATAR.getVisibility());
        assertEquals(AttachmentVisibility.PUBLIC, AttachmentBizType.CBTI_PERSONALITY.getVisibility());
        assertTrue(Arrays.stream(AttachmentBizType.values())
                .filter(type -> type != AttachmentBizType.AVATAR && type != AttachmentBizType.CBTI_PERSONALITY)
                .allMatch(type -> type.getVisibility() == AttachmentVisibility.PRIVATE));
    }
}
