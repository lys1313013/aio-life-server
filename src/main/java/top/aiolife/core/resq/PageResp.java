package top.aiolife.core.resq;

import java.util.List;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 分页数据返回对象
 *
 * @author Lys
 * @date 2024/9/25
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PageResp<T> {

  /**
   * 表格数据
   */
  private List<T> items;

  /**
   * 总数量
   */
  private Long total;

  public static <T> PageResp<T> of() {
      return new PageResp<>();
  }

  /** 仅转换记录字段，保留分页总数及空值语义。 */
  public <R> PageResp<R> map(Function<T, R> mapper) {
    return new PageResp<>(items == null ? null : items.stream().map(mapper).toList(), total);
  }

  /**
   * 创建分页数据返回对象
   *
   * @param rows 分页数据
   * @param total 总数量
   * @return 分页数据返回对象
   */
  public static <T> PageResp<T> of(List<T> rows, Integer total) {
    if (total == null) {
      return new PageResp<>(rows, null);
    } else {
      return new PageResp<>(rows, total.longValue());

    }
  }

  /**
   * 创建分页数据返回对象
   *
   * @param rows 分页数据
   * @param total 总数量
   * @return 分页数据返回对象
   */
  public static <T> PageResp<T> of(List<T> rows, Long total) {
    return new PageResp<>(rows, total);
  }
}
