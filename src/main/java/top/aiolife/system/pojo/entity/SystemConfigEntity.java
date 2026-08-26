package top.aiolife.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

/**
 * 系统配置（通用 KV）
 *
 * <p>全局系统级配置项，按 key 唯一。value 可以是纯文本、JSON、布尔、数字等，
 * 由 configType 字段指导前端渲染。典型用途：反馈通知接收人列表、邮件开关、功能灰度等。</p>
 *
 * @author Ethan
 * @date 2026/07/19
 */
@Getter
@Setter
@TableName("system_config")
public class SystemConfigEntity extends BaseEntity {

    /**
     * 配置键（唯一）
     */
    private String configKey;

    /**
     * 配置值（JSON 或纯文本）
     */
    private String configValue;

    /**
     * 配置类型：STRING / JSON / BOOLEAN / NUMBER
     */
    private String configType;

    /**
     * 配置说明
     */
    private String description;
}
