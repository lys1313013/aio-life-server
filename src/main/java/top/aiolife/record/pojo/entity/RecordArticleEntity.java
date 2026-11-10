package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.BaseEntity;

/** 收藏文章。 */
@Getter
@Setter
@ToString
@TableName("record_article")
public class RecordArticleEntity extends BaseEntity {

    private String url;

    private String title;

    private String author;

    private String contentHtml;

    private String category;

    private String tags;
}
