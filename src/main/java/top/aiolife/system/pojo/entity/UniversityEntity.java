package top.aiolife.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

/** 中国大陆高校公共目录。 */
@Getter
@Setter
@TableName("university")
public class UniversityEntity extends BaseEntity {
    private String name;

    /** 所在省级地区编码，与 sys_region.code 对应。 */
    private String provinceCode;
    private String remark;
}
