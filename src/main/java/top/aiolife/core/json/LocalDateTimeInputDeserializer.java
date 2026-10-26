package top.aiolife.core.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/** 业务本地时间严格使用 yyyy-MM-dd HH:mm:ss。 */
public class LocalDateTimeInputDeserializer extends JsonDeserializer<LocalDateTime> {
    private static final DateTimeFormatter STANDARD = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (LocalDateTime) context.handleUnexpectedToken(LocalDateTime.class, parser);
        }
        String value = parser.getText();
        try {
            return LocalDateTime.parse(value, STANDARD);
        } catch (DateTimeParseException invalid) {
            return (LocalDateTime) context.handleWeirdStringValue(LocalDateTime.class, value,
                    "日期时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }
}
