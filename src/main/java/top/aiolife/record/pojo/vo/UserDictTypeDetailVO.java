package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.pojo.entity.UserDictTypeEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 用户字典类型详情返回值
 *
 * @author Lys
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDictTypeDetailVO {
    private UserDictTypeEntity userDictTypeEntity;

    /**
     * 明细数据
     */
    private List<UserDictDataEntity> dictDetailList;
}
