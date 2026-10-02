package top.aiolife.record.pojo.vo;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 通用字典返回值
 *
 * @author Lys
 * @date 2025/04/05 22:33
 */
@Getter
@Setter
public class SysDictTypeDetailVO {
    private SysDictTypeVO sysDictTypeEntity;

    /**
     * 明细数据
     */
    private List<SysDictDataVO> dictDetailList;
}
