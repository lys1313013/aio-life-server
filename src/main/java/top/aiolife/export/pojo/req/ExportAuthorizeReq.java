package top.aiolife.export.pojo.req;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ExportAuthorizeReq {
    @NotBlank
    @Pattern(regexp = "CREATE|DOWNLOAD")
    private String purpose;

    @Size(max = 100)
    private List<@NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,63}") String> datasetKeys;

    private Long jobId;

    @NotBlank
    @Size(max = 200)
    private String password;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("未知导出参数");
    }
}
