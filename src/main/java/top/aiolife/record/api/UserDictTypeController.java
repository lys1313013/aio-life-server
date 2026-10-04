package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.enums.DictTypeEnum;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.pojo.entity.UserDictTypeEntity;
import top.aiolife.record.pojo.vo.UserDictTypeDetailVO;
import top.aiolife.record.service.UserDictDataService;

/**
 * 用户字典类型Controller
 *
 * @author Lys
 */
@RestController
@AllArgsConstructor
@RequestMapping("/userDictType")
public class UserDictTypeController {

    private UserDictDataService userDictDataService;

    @GetMapping("/dictTypeEnum")
    public ApiResponse<List<Map<String, String>>> dictTypeEnum() {
        return ApiResponse.success(DictTypeEnum.toList());
    }

    @GetMapping("/getByDictType")
    public ApiResponse<UserDictTypeDetailVO> getByDictType(String dictType) {
        Long userId = StpUtil.getLoginIdAsLong();

        // 1. 直接用枚举构造前端需要的虚拟类型信息，替换掉查库
        UserDictTypeEntity typeVO = new UserDictTypeEntity();
        typeVO.setDictType(dictType);
        for (Map<String, String> map : DictTypeEnum.toList()) {
            if (dictType.equals(map.get("value"))) {
                typeVO.setDictName(map.get("label"));
                break;
            }
        }

        // 2. 正常查询数据明细
        List<UserDictDataEntity> dataList = userDictDataService.listUserVisibleDictData(userId, dictType);

        UserDictTypeDetailVO detailVO = new UserDictTypeDetailVO();
        detailVO.setUserDictTypeEntity(RecordApiConvertor.INSTANCE.toUserDictTypeVO(typeVO));
        detailVO.setDictDetailList(RecordApiConvertor.INSTANCE.toUserDictDataVOList(dataList));

        return ApiResponse.success(detailVO);
    }
}
