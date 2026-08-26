package top.aiolife.record.pojo.entity;

import top.aiolife.core.pojo.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 密码库实体
 *
 * @author Lys
 * @date 2026/04/28
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("password_vault")
public class PasswordVaultEntity extends BaseEntity {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 标题
     */
    private String title;

    /**
     * 网站/应用名
     */
    private String website;

    /**
     * 分类：工作/生活/金融/社交/其他
     */
    private String category;

    /**
     * 账号
     */
    private String username;

    /**
     * 密码（SM4加密存储）
     */
    private String password;

    /**
     * PBKDF2盐值，每条记录唯一
     */
    private String salt;

    /**
     * 备注（SM4加密存储）
     */
    private String remark;

    /**
     * 是否收藏
     */
    private Boolean favorite;
}