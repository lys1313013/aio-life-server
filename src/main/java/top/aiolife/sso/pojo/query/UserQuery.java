package top.aiolife.sso.pojo.query;

import lombok.Data;

/** 管理员用户列表的实际筛选条件。 */
@Data
public class UserQuery {
    private String keyword;
}
