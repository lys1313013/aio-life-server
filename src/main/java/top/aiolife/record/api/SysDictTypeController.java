package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.convertor.SysDictDataConvertor;
import top.aiolife.record.pojo.vo.SysDictDataVO;
import top.aiolife.record.pojo.vo.SysDictTypeDetailVO;
import top.aiolife.record.service.ISysDictService;

import java.util.List;

/**
 * 系统字典类型查询控制器。
 *
 * @author Lys
 * @date 2025/04/05 22:17
 */
@Tag(name = "系统字典类型")
@RestController
@AllArgsConstructor
@RequestMapping("/sysDictType")
public class SysDictTypeController {

    private final ISysDictService sysDictService;

    @Operation(summary = "查询系统字典类型及明细")
    @GetMapping("/getByDictType")
    public ApiResponse<SysDictTypeDetailVO> queryById(
            @Parameter(description = "系统字典类型标识") String dictType) {
        ISysDictService.DictTypeDetail detail = sysDictService.getDictTypeDetail(dictType);
        if (detail == null) {
            return ApiResponse.success();
        }
        SysDictTypeDetailVO sysDictTypeDetailVO = new SysDictTypeDetailVO();
        sysDictTypeDetailVO.setSysDictTypeEntity(
                RecordApiConvertor.INSTANCE.toSysDictTypeVO(detail.type()));
        List<SysDictDataVO> detailVoList = detail.data().stream()
                .map(SysDictDataConvertor.INSTANCE::Entity2VO)
                .toList();
        sysDictTypeDetailVO.setDictDetailList(detailVoList);
        return ApiResponse.success(sysDictTypeDetailVO);
    }
}
