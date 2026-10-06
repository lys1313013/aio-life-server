package top.aiolife.core.constant;

/**
 * 返回值状态码常量类
 *
 * @author Lys
 * @date 2024/9/25
 */
public class ResponseCodeConst {

  /**
   * 统一公用业务成功返回码
   */
  public static final int SUCCESS = 0;

  /**
   * 通用异常
   */
  public static final int COMMON_FAIL = 113000;


  /**
   * 参数校验失败
   */
  public static final int PARAM_FAIL = 100400;

  /**
   * 二级锁未验证
   */
  public static final int SECONDARY_LOCK_REQUIRED = 2001;
}