package top.aiolife.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.aiolife.core.resq.PageResp;

import static org.junit.jupiter.api.Assertions.*;

class PageRespJsonTest {

  @Test
  void 分页总数为数字且长ID和文件大小保持字符串() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
        .withUserConfiguration(JsonConfig.class)
        .run(context -> {
          ObjectMapper mapper = context.getBean(ObjectMapper.class);
          var page = PageResp.of(List.of(Map.of("id", 9007199254740993L, "fileSize", 0L)),
              2147483648L);
          var json = mapper.readTree(mapper.writeValueAsString(page));
          assertTrue(json.path("total").isIntegralNumber());
          assertEquals(2147483648L, json.path("total").longValue());
          assertEquals("9007199254740993", json.at("/items/0/id").textValue());
          assertEquals("0", json.at("/items/0/fileSize").textValue());
          assertEquals(page.getTotal(), mapper.readValue(json.toString(), PageResp.class).getTotal());

          var empty = mapper.readTree(mapper.writeValueAsString(PageResp.of(List.of(), 0)));
          assertTrue(empty.path("items").isEmpty());
          assertTrue(empty.path("total").isIntegralNumber());
          assertEquals(0, empty.path("total").intValue());
        });
  }

  @Test
  void 空值和映射保留分页原有语义() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
        .withUserConfiguration(JsonConfig.class)
        .run(context -> {
          ObjectMapper mapper = context.getBean(ObjectMapper.class);
          var unspecified = mapper.readTree(mapper.writeValueAsString(PageResp.of()));
          assertTrue(unspecified.path("items").isNull());
          assertTrue(unspecified.path("total").isNull());
          var mapped = PageResp.of(List.of(7), 21L).map(String::valueOf);
          assertEquals(List.of("7"), mapped.getItems());
          assertEquals(21L, mapped.getTotal());
          assertTrue(mapper.valueToTree(mapped).path("total").isIntegralNumber());
          assertNull(PageResp.of(List.of(), (Integer) null).getTotal());
          assertNull(PageResp.of(List.of(), (Long) null).getTotal());
          var nullItems = PageResp.<Integer>of(null, (Long) null).map(String::valueOf);
          assertNull(nullItems.getItems());
          assertNull(nullItems.getTotal());
        });
  }
}
