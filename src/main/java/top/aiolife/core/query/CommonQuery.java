package top.aiolife.core.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

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

  /** 保留分页参数，将接口筛选模型转换成业务查询条件。 */
  public <R> CommonQuery<R> map(java.util.function.Function<T, R> mapper) {
    CommonQuery<R> result = new CommonQuery<>();
    result.setPage(page);
    result.setPageSize(pageSize);
    result.setCondition(condition == null ? null : mapper.apply(condition));
    return result;
  }
}
