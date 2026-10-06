package top.aiolife.core.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/** 将显式标注的计数字段输出为 JSON 数字，覆盖全局 Long 字符串配置。 */
public class CountSerializer extends JsonSerializer<Long> {

  @Override
  public void serialize(Long value, JsonGenerator generator, SerializerProvider provider)
      throws IOException {
    generator.writeNumber(value);
  }
}
