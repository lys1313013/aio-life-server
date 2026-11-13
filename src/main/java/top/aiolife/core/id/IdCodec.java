package top.aiolife.core.id;

import java.util.List;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.exception.ApiException;

/**
 * 接口边界的字符串 ID 转换。边界统一字符串，持久层仍使用 long。
 */
public final class IdCodec {

    private IdCodec() {
    }

    public static long decode(String id) {
        if (id == null || id.isBlank()) {
            throw new ApiException(ResponseCodeConst.PARAM_FAIL, "ID 不能为空");
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw new ApiException(ResponseCodeConst.PARAM_FAIL, "ID 格式错误");
        }
    }

    public static Long decodeOrNull(String id) {
        return id == null || id.isBlank() ? null : decode(id);
    }

    public static List<Long> decodeAll(List<String> ids) {
        return ids == null ? null : ids.stream().map(IdCodec::decode).toList();
    }

    public static String encode(Long id) {
        return id == null ? null : id.toString();
    }
}
