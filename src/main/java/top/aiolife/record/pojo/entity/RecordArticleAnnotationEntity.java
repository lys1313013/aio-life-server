package top.aiolife.record.pojo.entity;

import top.aiolife.core.pojo.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 收藏文章标注。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("record_article_annotation")
public class RecordArticleAnnotationEntity extends BaseEntity {

    private Long articleId;

    private String selectedText;

    private String noteContent;

    private String startContainerPath;

    private Integer startOffset;

    private String endContainerPath;

    private Integer endOffset;

    private String color;
}
