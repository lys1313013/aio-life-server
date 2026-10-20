package top.aiolife.record.notification;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;

/** 一期只支持力扣未打卡提醒；字段名必须与微信后台选用的模板一致。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "aio.life.server.notification.wechat-mini")
public class WechatSubscriptionProperties {
    private boolean enabled;
    private String templateId = "";
    private String titleField = "thing1";
    private String contentField = "thing2";
    private String timeField = "time3";
    private String environment = "formal";

    public boolean configured() {
        if (!enabled || !StringUtils.hasText(templateId)
                || !List.of("formal", "trial", "developer").contains(environment)) return false;
        var keys = new HashSet<String>();
        for (String field : List.of(titleField, contentField)) {
            if (StringUtils.hasText(field) && (!field.matches("thing[1-9][0-9]*") || !keys.add(field))) return false;
        }
        if (StringUtils.hasText(timeField) && (!timeField.matches("time[1-9][0-9]*") || !keys.add(timeField))) return false;
        return !keys.isEmpty();
    }
}
