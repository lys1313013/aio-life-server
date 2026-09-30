package top.aiolife.core.resq;

import io.swagger.v3.oas.annotations.media.Schema;
import top.aiolife.core.constant.ResponseCodeConst;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;


/**
 * 接口统一返回值
 *
 * @author Lys
 * @date 2024/9/25
 */
@Setter
@Getter
@NoArgsConstructor
public class ApiResponse<T> implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * 状态码
   */
  @Schema(description = "业务状态码：0=成功，113000=通用失败，100400=参数错误，2001=需要二级密码验证；HTTP 200 不代表业务成功", example = "0")
  private String rscode;

  /**
   * 返回信息，一般是报错提示
   */
  @Schema(description = "结果提示，失败时通常为错误信息，成功时可为 null", nullable = true)
  private String result;

  /**
   * 返回结果
   */
  @Schema(description = "业务数据；rscode=2001 时为包含 menuPath 的对象", nullable = true)
  private T data;

  public ApiResponse(String rscode, String result, T data) {
    this.rscode = rscode;
    this.result = result;
    this.data = data;
  }

  public static <T> ApiResponse<T> success() {
    return new ApiResponse<>(ResponseCodeConst.RSCODE_SUCCESS, null, null);
  }

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(ResponseCodeConst.RSCODE_SUCCESS, null, data);
  }

  public static <T> ApiResponse<T> success(T data, String result) {
    return new ApiResponse<>(ResponseCodeConst.RSCODE_SUCCESS, result, data);
  }

  /**
   * 只有错误码的返回值，不推荐使用
   *
   * @param rscode 状态码
   * @author Lys
   * @date 2024/9/26
   */
  public static <T> ApiResponse<T> error(String rscode) {
    return new ApiResponse<>(rscode, null, null);
  }

  /**
   * 返回状态码和提示信息
   *
   * @param rscode 状态码
   * @param result 提示信息
   * @author Lys
   * @date 2024/9/26
   */
  public static <T> ApiResponse<T> error(String rscode, String result) {
    return new ApiResponse<>(rscode, result, null);
  }

  /**
   * 返回状态码、提示信息和数据
   *
   * @param rscode 状态码
   * @param result 提示信息
   * @param data 数据
   * @author Lys
   * @date 2024/9/26
   */
  public static <T> ApiResponse<T> error(String rscode, String result, T data) {
    return new ApiResponse<>(rscode, result, data);
  }
}
