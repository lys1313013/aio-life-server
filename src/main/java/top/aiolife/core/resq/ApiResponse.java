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
   * 业务状态码
   */
  @Schema(description = "业务状态码：0=成功，113000=通用失败，100400=参数错误，2001=需要二级密码验证；HTTP 200 不代表业务成功", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
  private int code;

  /**
   * 提示信息
   */
  @Schema(description = "供客户端展示的提示信息，成功时可为空", nullable = true)
  private String message;

  /**
   * 业务数据
   */
  @Schema(description = "接口返回的业务数据", nullable = true)
  private T data;

  public ApiResponse(int code, String message, T data) {
    this.code = code;
    this.message = message;
    this.data = data;
  }

  public static <T> ApiResponse<T> success() {
    return new ApiResponse<>(ResponseCodeConst.SUCCESS, null, null);
  }

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(ResponseCodeConst.SUCCESS, null, data);
  }

  public static <T> ApiResponse<T> success(T data, String message) {
    return new ApiResponse<>(ResponseCodeConst.SUCCESS, message, data);
  }

  /**
   * 使用通用失败码返回提示信息
   *
   * @param message 提示信息
   */
  public static <T> ApiResponse<T> error(String message) {
    return error(ResponseCodeConst.COMMON_FAIL, message);
  }

  /**
   * 只有错误码的返回值，不推荐使用
   *
   * @param code 业务状态码
   * @author Lys
   * @date 2024/9/26
   */
  public static <T> ApiResponse<T> error(int code) {
    return new ApiResponse<>(code, null, null);
  }

  /**
   * 返回状态码和提示信息
   *
   * @param code 业务状态码
   * @param message 提示信息
   * @author Lys
   * @date 2024/9/26
   */
  public static <T> ApiResponse<T> error(int code, String message) {
    return new ApiResponse<>(code, message, null);
  }

  /**
   * 返回状态码、提示信息和数据
   *
   * @param code 业务状态码
   * @param message 提示信息
   * @param data 数据
   * @author Lys
   * @date 2024/9/26
   */
  public static <T> ApiResponse<T> error(int code, String message, T data) {
    return new ApiResponse<>(code, message, data);
  }
}
