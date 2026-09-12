package top.aiolife.core.query;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 从 URL 查询参数绑定 DTO，沿用 JSON 的日期、枚举和泛型转换规则。
 * CommonQuery 的筛选字段使用平铺参数，例如 page=1&status=in_progress。
 * 数组使用重复参数，例如 statuses=in_progress&statuses=on_hold。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface QueryParams {
}
