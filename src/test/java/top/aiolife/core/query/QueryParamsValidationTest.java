package top.aiolife.core.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.exception.ExceptionHandle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QueryParamsValidationTest {

    private MockMvc mvc(QueryController controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new QueryParamsArgumentResolver(
                        new ObjectMapper().findAndRegisterModules()))
                .setControllerAdvice(new ExceptionHandle())
                .build();
    }

    @Test
    void testValid_缺少字段时拦截且合法日期可进入业务() throws Exception {
        QueryController controller = new QueryController();
        MockMvc mvc = mvc(controller);
        mvc.perform(get("/required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("date 不能为空"));
        assertEquals(0, controller.calls);
        mvc.perform(get("/required").param("date", "2026-10-04"))
                .andExpect(status().isOk())
                .andExpect(content().string("2026-10-04"));
        assertEquals(1, controller.calls);
    }

    @Test
    void testOptional_未标校验注解的查询保持原有绑定行为() throws Exception {
        QueryController controller = new QueryController();
        mvc(controller).perform(get("/optional"))
                .andExpect(status().isOk())
                .andExpect(content().string("optional"));
        assertEquals(1, controller.calls);
    }

    @Test
    void testValidated_按声明分组校验() throws Exception {
        QueryController controller = new QueryController();
        MockMvc mvc = mvc(controller);
        mvc.perform(get("/grouped"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("分组日期不能为空"));
        assertEquals(0, controller.calls);
        mvc.perform(get("/grouped-default"))
                .andExpect(status().isOk())
                .andExpect(content().string("optional"));
        mvc.perform(get("/grouped").param("date", "2026-10-04"))
                .andExpect(status().isOk())
                .andExpect(content().string("2026-10-04"));
        assertEquals(2, controller.calls);
    }

    public interface RequiredDate {}

    public record RequiredQuery(
            @NotNull(message = "date 不能为空")
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date) {}

    public record GroupedQuery(
            @NotNull(groups = RequiredDate.class, message = "分组日期不能为空")
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date) {}

    @RestController
    static class QueryController {
        int calls;

        private String result(LocalDate date) {
            calls++;
            return date == null ? "optional" : date.toString();
        }

        @GetMapping("/required")
        String required(@Valid @QueryParams RequiredQuery query) {
            return result(query.date());
        }

        @GetMapping("/optional")
        String optional(@QueryParams RequiredQuery query) {
            return result(query.date());
        }

        @GetMapping("/grouped")
        String grouped(@Validated(RequiredDate.class) @QueryParams GroupedQuery query) {
            return result(query.date());
        }

        @GetMapping("/grouped-default")
        String groupedDefault(@Valid @QueryParams GroupedQuery query) {
            return result(query.date());
        }
    }
}
