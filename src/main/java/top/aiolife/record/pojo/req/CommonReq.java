package top.aiolife.record.pojo.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 通用处理
 *
 * @author Lys
 * @date 2025/10/05 23:07
 */
@Data
public class CommonReq {

    @NotEmpty
    @Size(max = 200)
    private List<@NotBlank String> idList;
}
