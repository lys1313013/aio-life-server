package top.aiolife.core.query;

import java.util.function.Function;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
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
public class CommonQuery<T> implements PaginationRequest {

  /**
   * 当前页数
   */
  @Schema(description = "当前页码，从 1 开始", defaultValue = "1")
  @NotNull @Min(1)
  private Integer page = 1;

  /**
   * 每页条数
   */
  @Schema(description = "每页条数", defaultValue = "50")
  @NotNull @Min(1) @Max(MAX_PAGE_SIZE)
  private Integer pageSize = 50;

  T condition;

  public void setPage(Integer page) {
    if (page == null || page < 1) throw new IllegalArgumentException("page 必须大于等于 1");
    this.page = page;
  }

  public void setPageSize(Integer pageSize) {
    if (pageSize == null) throw new IllegalArgumentException("pageSize 不能为空");
    PaginationRequest.validate(page, pageSize, MAX_PAGE_SIZE);
    this.pageSize = pageSize;
  }

  /** 保留分页参数，将接口筛选模型转换成业务查询条件。 */
  public <R> CommonQuery<R> map(Function<T, R> mapper) {
    CommonQuery<R> result = new CommonQuery<>();
    result.setPage(page);
    result.setPageSize(pageSize);
    result.setCondition(condition == null ? null : mapper.apply(condition));
    return result;
  }
}
