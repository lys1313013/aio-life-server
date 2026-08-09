package top.aiolife.relationship.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import java.time.LocalDateTime;

/**
 * 人物节点
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RelationshipProperties
public class PersonNode {

    @Id
    @GeneratedValue
    private Long id;

    /**
     * 用户ID（所属用户）
     */
    @Property("userId")
    private Long userId;

    /**
     * 姓名
     */
    @Property("name")
    private String name;

    /**
     * 头像
     */
    @Property("avatar")
    private String avatar;

    /**
     * 分类：亲属/社会/情感/其他
     */
    @Property("category")
    private String category;

    /**
     * 简介
     */
    @Property("description")
    private String description;

    /**
     * 标签，逗号分隔
     */
    @Property("tags")
    private String tags;

    /**
     * 生日
     */
    @Property("birthday")
    private String birthday;

    /**
     * 电话
     */
    @Property("phone")
    private String phone;

    /**
     * 邮箱
     */
    @Property("email")
    private String email;

    /**
     * 学校
     */
    @Property("school")
    private String school;

    /**
     * 社交链接，JSON格式
     */
    @Property("socialLinks")
    private String socialLinks;

    /**
     * 备注
     */
    @Property("notes")
    private String notes;

    /**
     * 创建时间
     */
    @Property("createdAt")
    private LocalDateTime createdAt;

    /**
     * 修改时间
     */
    @Property("updatedAt")
    private LocalDateTime updatedAt;
}
