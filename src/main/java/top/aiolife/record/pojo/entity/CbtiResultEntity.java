package top.aiolife.record.pojo.entity;

import top.aiolife.core.pojo.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * CBTI 测试历史实体
 *
 * @author Ethan
 * @date 2026/04/18
 */
@Getter
@Setter
@TableName("cbti_result")
public class CbtiResultEntity extends BaseEntity {

    private Long userId;

    private String personalityCode;

    private Integer similarity;

    private String dimensions;

    private String answers;

    private String hiddenAnswers;
}

