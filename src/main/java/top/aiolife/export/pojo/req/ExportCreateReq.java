package top.aiolife.export.pojo.req;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ExportCreateReq {
    @NotEmpty
    @Size(max = 100)
    private List<@NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,63}") String> datasetKeys;

    @NotBlank
    @Size(max = 64)
    private String catalogVersion;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("未知导出参数");
    }
}
