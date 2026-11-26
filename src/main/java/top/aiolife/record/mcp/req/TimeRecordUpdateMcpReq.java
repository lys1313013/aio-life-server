package top.aiolife.record.mcp.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import dev.langchain4j.model.output.structured.Description;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import java.time.LocalDate;
import lombok.Data;
import org.hibernate.validator.constraints.CodePointLength;

/** MCP 时迹部分更新请求。 */
@Data
public class TimeRecordUpdateMcpReq {

    @Description("必填，时迹 ID 字符串，通过 time_record_queryByDateRange 获取")
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]*$")
    private String id;

    @Description("分类 ID 字符串，通过 time_tracker_category_list 获取")
    @Pattern(regexp = "^[1-9][0-9]*$")
    private String categoryId;

    @Description("新日期，格式 yyyy-MM-dd。已有运动明细随日期迁移")
    @JsonFormat(pattern = "uuuu-MM-dd", lenient = OptBoolean.FALSE)
    private LocalDate date;

    @Description("开始分钟（0–1439，包含该分钟）。省略则使用原开始分钟")
    @JsonDeserialize(using = MinuteDeserializer.class)
    @Min(0) @Max(1439)
    private Integer startMinute;

    @Description("结束分钟（0–1439，包含该分钟）。省略则使用原结束分钟，合并后不得早于开始分钟")
    @JsonDeserialize(using = MinuteDeserializer.class)
    @Min(0) @Max(1439)
    private Integer endMinute;

    @Description("标题，最多 100 字符。空字符串清空标题")
    @CodePointLength(max = 100)
    private String title;

    @Description("描述。空字符串清空描述")
    private String description;

    static class MinuteDeserializer extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return context.reportInputMismatch(Integer.class, "分钟必须使用 JSON 整数");
            }
            return parser.getIntValue();
        }
    }
}
