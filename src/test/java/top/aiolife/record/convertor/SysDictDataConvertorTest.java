package top.aiolife.record.convertor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import top.aiolife.record.pojo.entity.SysDictDataEntity;
import top.aiolife.record.pojo.vo.SysDictDataVO;

class SysDictDataConvertorTest {

    @Test
    void 字典ID按雪花长整型返回不被截断() {
        var entity = new SysDictDataEntity();
        entity.setDictCode(9007199254740993L);
        entity.setDictLabel("测试");
        entity.setDictValue("fixture");

        SysDictDataVO vo = SysDictDataConvertor.INSTANCE.Entity2VO(entity);

        assertEquals(9007199254740993L, vo.getId());
        assertEquals("测试", vo.getLabel());
        assertEquals("fixture", vo.getValue());
    }
}
