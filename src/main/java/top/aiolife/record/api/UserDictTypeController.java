package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import cn.dev33.satoken.stp.StpUtil;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.enums.DictTypeEnum;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.pojo.vo.UserDictTypeDetailVO;
import top.aiolife.record.pojo.vo.UserDictTypeVO;
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

    /**
     * 获取用户字典类型枚举，返回类型标识和中文名称。
     */
    @Operation(summary = "查询用户字典类型")
    @GetMapping("/dictTypeEnum")
    public ApiResponse<List<Map<String, String>>> dictTypeEnum() {
        return ApiResponse.success(DictTypeEnum.toList());
    }

    /**
     * 按字典类型查询当前用户可见的已启用字典数据及类型信息。
     */
    @Operation(summary = "按类型查询已启用的可见用户字典")
    @GetMapping("/getByDictType")
    public ApiResponse<UserDictTypeDetailVO> getByDictType(@Parameter(description = "用户字典类型，取自 dictTypeEnum") String dictType) {
        Long userId = StpUtil.getLoginIdAsLong();

        // 1. 字典类型展示信息由枚举提供，不映射数据库实体。
        UserDictTypeVO typeVO = new UserDictTypeVO();
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
        detailVO.setUserDictTypeEntity(typeVO);
        detailVO.setDictDetailList(RecordApiConvertor.INSTANCE.toUserDictDataVOList(dataList));

        return ApiResponse.success(detailVO);
    }
}
