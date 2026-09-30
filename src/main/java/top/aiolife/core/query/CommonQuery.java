package top.aiolife.core.query;

import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 分页查询；URL 中筛选字段与 page、pageSize 平铺，不传 condition 对象。
 *
 * @author lys
 * @date 2025/2/26
 */
@Setter
@Getter
public class CommonQuery<T> {

  /**
   * 当前页数
   */
  @Schema(description = "当前页码，从 1 开始", defaultValue = "1")
  private Integer page = 1;

  /**
   * 每页条数
   */
  @Schema(description = "每页条数", defaultValue = "50")
  private Integer pageSize = 50;

  T condition;
}
