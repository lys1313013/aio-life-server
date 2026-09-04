package top.aiolife.record.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 用户字典排序结果。
 */
@Data
@AllArgsConstructor
public class UserDictDataSortVO {

    private Long id;

    private Integer dictSort;
}
