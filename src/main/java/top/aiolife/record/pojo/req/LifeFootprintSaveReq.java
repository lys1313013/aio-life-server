package top.aiolife.record.pojo.req;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LifeFootprintSaveReq {
    @NotBlank @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}", message = "请填写完整日期 YYYY-MM-DD")
    private String startDate;
    @NotBlank @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}", message = "请填写完整日期 YYYY-MM-DD")
    private String endDate;
    @NotBlank @Size(max = 100)
    private String placeName;
    @Size(max = 500)
    private String address;
    @Size(max = 100)
    private String city;
    @Pattern(regexp = "[0-9]{2,12}")
    private String regionCode;
    @NotNull @DecimalMin("-180") @DecimalMax("180")
    private BigDecimal longitude;
    @NotNull @DecimalMin("-90") @DecimalMax("90")
    private BigDecimal latitude;
    @NotBlank @Pattern(regexp = "GCJ02")
    private String coordinateSystem;
    @Size(max = 128)
    private String providerUid;
    @Size(max = 5000)
    private String note;
}
