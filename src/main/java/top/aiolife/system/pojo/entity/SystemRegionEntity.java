package top.aiolife.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

/** 省、市、区县、乡镇街道基础目录；编码按来源保留字符串。 */
@Getter
@Setter
@TableName("system_region")
public class SystemRegionEntity extends BaseEntity {
    private String code;
    private String parentCode;
    private String name;
    private Integer level;
    private String dataVersion;
}
