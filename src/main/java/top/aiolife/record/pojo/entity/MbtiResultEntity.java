package top.aiolife.record.pojo.entity;

import top.aiolife.core.pojo.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * MBTI测试结果实体
 *
 * @author Lys
 * @date 2026-03-23
 */
@Getter
@Setter
@TableName("mbti_result")
public class MbtiResultEntity extends BaseEntity {

    private Long userId;

    private String testId;

    private String mbtiType;

    private String rawResult;

    private String resultsPage;
}
