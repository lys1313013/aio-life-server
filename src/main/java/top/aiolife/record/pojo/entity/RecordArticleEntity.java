package top.aiolife.record.pojo.entity;

import top.aiolife.core.pojo.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 收藏文章。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("record_article")
public class RecordArticleEntity extends BaseEntity {

    private String url;

    private String title;

    private String author;

    private String contentHtml;

    private String category;

    private String tags;
}
