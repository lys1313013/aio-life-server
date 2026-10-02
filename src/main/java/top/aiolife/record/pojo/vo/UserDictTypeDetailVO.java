package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户字典类型详情返回值
 *
 * @author Lys
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDictTypeDetailVO {
    private UserDictTypeVO userDictTypeEntity;

    /**
     * 明细数据
     */
    private List<UserDictDataVO> dictDetailList;
}
