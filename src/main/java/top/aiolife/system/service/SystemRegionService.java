package top.aiolife.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.system.mapper.SystemRegionMapper;
import top.aiolife.system.pojo.entity.SystemRegionEntity;
import top.aiolife.system.pojo.vo.SystemRegionVO;

@Service
@RequiredArgsConstructor
public class SystemRegionService {
    private final SystemRegionMapper mapper;

    /** 每次只取一个父级的子项；不向客户端发送整个乡镇库。 */
    public List<SystemRegionVO> children(String parentCode) {
        String parent = parentCode == null || parentCode.isBlank() ? "0" : parentCode;
        checkCode(parent);
        var rows = mapper.selectList(new LambdaQueryWrapper<SystemRegionEntity>()
                .eq(SystemRegionEntity::getParentCode, parent).orderByAsc(SystemRegionEntity::getCode));
        if (rows.isEmpty()) return List.of();
        Set<String> parents = mapper.selectList(new LambdaQueryWrapper<SystemRegionEntity>()
                .select(SystemRegionEntity::getParentCode)
                .in(SystemRegionEntity::getParentCode, rows.stream().map(SystemRegionEntity::getCode).toList()))
                .stream().map(SystemRegionEntity::getParentCode).collect(Collectors.toSet());
        return rows.stream().map(row -> vo(row, parents.contains(row.getCode()))).toList();
    }

    /** 从已选编码恢复完整路径，不依赖编码长度推断直辖市等层级。 */
    public List<SystemRegionVO> path(String code) {
        checkCode(code);
        var result = new ArrayList<SystemRegionVO>();
        String current = code;
        while (!"0".equals(current)) {
            var row = require(current);
            if (result.size() == 4 || result.stream().anyMatch(item -> item.code().equals(row.getCode())))
                throw new IllegalStateException("地区目录层级异常");
            boolean hasChildren = mapper.exists(new LambdaQueryWrapper<SystemRegionEntity>()
                    .eq(SystemRegionEntity::getParentCode, current));
            result.add(vo(row, hasChildren));
            current = row.getParentCode();
        }
        Collections.reverse(result);
        return result;
    }

    public void validateSelection(String code) {
        if (code != null) { checkCode(code); require(code); }
    }
    private SystemRegionEntity require(String code) {
        var row = mapper.selectOne(new LambdaQueryWrapper<SystemRegionEntity>().eq(SystemRegionEntity::getCode, code));
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "地区已停用或不存在，请重新选择");
        return row;
    }
    private void checkCode(String code) {
        if (code == null || !code.matches("[0-9]{1,12}")) throw new IllegalArgumentException("地区编码无效");
    }
    private SystemRegionVO vo(SystemRegionEntity row, boolean children) {
        return new SystemRegionVO(row.getCode(), row.getName(), row.getLevel(), children);
    }
}
