package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LifeFootprintVO {
    private Long id;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate endDate;
    private String placeName;
    private String address;
    private String city;
    private String regionCode;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String coordinateSystem;
    private String providerUid;
    private String note;
}
