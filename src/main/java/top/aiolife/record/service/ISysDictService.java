package top.aiolife.record.service;

import top.aiolife.core.resq.PageResp;
import top.aiolife.record.pojo.entity.SysDictDataEntity;
import top.aiolife.record.pojo.entity.SysDictTypeEntity;
import top.aiolife.record.pojo.query.SysDictTypeQuery;

import java.util.List;

/**
 * 系统字典服务接口。
 *
 * @author Lys
 * @date 2025/10/03 14:24
 */
public interface ISysDictService {

    List<SysDictDataEntity> getDictDataByDictType(String dictType);

    /**
     * 字典类型及明细，类型不存在时返回 null。
     */
    DictTypeDetail getDictTypeDetail(String dictType);

    PageResp<SysDictDataEntity> pageSysDictData(SysDictTypeQuery condition, int page, int pageSize);

    boolean createSysDictData(SysDictDataEntity entity, long userId);

    boolean updateSysDictData(long dictCode, SysDictDataEntity entity, long userId);

    boolean deleteSysDictData(long dictCode, long userId);

    PageResp<SysDictTypeEntity> pageSysDictType(String dictName, int page, int pageSize);

    boolean createSysDictType(SysDictTypeEntity entity, long userId);

    boolean updateSysDictType(long dictId, SysDictTypeEntity entity, long userId);

    boolean deleteSysDictType(long dictId, long userId);

    record DictTypeDetail(SysDictTypeEntity type, List<SysDictDataEntity> data) {
    }
}
