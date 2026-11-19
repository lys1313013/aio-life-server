package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

@Getter
@Setter
@TableName("creative_work")
public class CreativeWorkEntity extends BaseEntity {
    private String kind;
    private String title;
    private String director;
    private String castNames;
    private String author;
    private Integer durationMinutes;
    private Integer episodeCount;
    private Integer pageCount;
    private String platform;
    private String resourceType;
    private String externalKey;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String coverUrl;
    private LocalDateTime metadataReadyTime;
}
