package top.aiolife.record.pojo.req;

import lombok.Data;

/**
 * 用户字典拖拽排序请求。
 */
@Data
public class UserDictDataReSortReq {

    /**
     * 字典类型。
     */
    private String dictType;

    /**
     * 被拖动的字典数据 ID。
     */
    private Long dragId;

    /**
     * 目标字典数据 ID。
     */
    private Long targetId;

    /**
     * 相对目标位置：before 或 after。
     */
    private String position;
}
