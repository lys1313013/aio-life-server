package top.aiolife.capture;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.BeanWrapperImpl;
import top.aiolife.capture.pojo.dto.CaptureSchemas.DoubanDraft;
import top.aiolife.capture.pojo.dto.CaptureSchemas.DoubanMetadata;

import static org.junit.jupiter.api.Assertions.*;

class CaptureSchemasEqualityTest {

    static Stream<Arguments> draftFields() {
        return Stream.of(
                Arguments.of("title", "测试书名"),
                Arguments.of("author", "测试作者"),
                Arguments.of("director", "测试导演"),
                Arguments.of("type", 1),
                Arguments.of("totalProgress", 100),
                Arguments.of("coverImgUrl", "https://example.com/cover.png"),
                Arguments.of("url", "https://example.com/subject/1"),
                Arguments.of("mediaType", "book"),
                Arguments.of("subjectId", "1"),
                Arguments.of("status", "in_progress"),
                Arguments.of("currentProgress", 10));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("draftFields")
    void 草稿按全部父类及自身字段进行值比较(String property, Object value) {
        var first = new DoubanDraft();
        var second = new DoubanDraft();
        assertEquals(first, second, "DTO 不使用实体的无主键身份规则");
        new BeanWrapperImpl(first).setPropertyValue(property, value);
        assertNotEquals(first, second);
        assertNotEquals(second, first);
        assertEquals(2, new HashSet<>(List.of(first, second)).size());

        new BeanWrapperImpl(second).setPropertyValue(property, value);
        assertEquals(first, second);
        assertEquals(second, first);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(1, new HashSet<>(List.of(first, second)).size());
    }

    @Test
    void 父类与草稿之间保持对称的不相等关系() {
        var metadata = new DoubanMetadata();
        metadata.setTitle("测试书名");
        var draft = new DoubanDraft();
        draft.setTitle("测试书名");
        assertNotEquals(metadata, draft);
        assertNotEquals(draft, metadata);
    }
}
