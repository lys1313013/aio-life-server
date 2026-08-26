package top.aiolife.record.pojo.entity;

import top.aiolife.core.pojo.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * CBTI 人格类型实体
 *
 * @author Ethan
 * @date 2026/04/18
 */
@Getter
@Setter
@TableName("cbti_personality")
public class CbtiPersonalityEntity extends BaseEntity {

    private String code;

    private String name;

    private String motto;

    private String color;

    private String vector;

    private String description;

    private String strengths;

    private String weaknesses;

    private String techStack;

    private String spirit;

    private String imageObject;

    private Integer isSpecial;
}

